import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type SubmitEvent } from 'react';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { CharacterBannerView, homeBannerKey } from '../catalog/CharacterBanner.tsx';
import '../catalog/catalog.css';
import { newIdempotencyKey } from './adminSession.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import {
  fetchBanner,
  hideBanner,
  publishBanner,
  saveBannerTexts,
  uploadBannerPhoto,
  type AdminBanner,
  type BannerField,
  type BannerTexts,
  type PhotoAnswer,
} from './homeBannerApi.ts';
import { shrinkPhoto, uploadName } from './shrinkPhoto.ts';

const messages = esAdmin.homeBanner;
const ACCEPTED_TYPES = 'image/jpeg,image/png,image/webp';
const EMPTY: BannerTexts = { title: '', body: '', linkText: '', linkPath: '' };

const bannerKey = ['admin', 'homeBanner'] as const;

/** The limits of UC-32, so the form never takes more than the server does. */
const MAX_LENGTH: Record<BannerField, number> = { title: 60, body: 200, linkText: 30, linkPath: 200 };

/**
 * The photo being uploaded. Its key and shrunk bytes are kept, so "Retry" sends exactly the same upload and the
 * server never stores it twice (UC-06).
 */
interface PendingPhoto {
  file: File;
  key: string;
  bytes: Blob | null;
  state: 'shrinking' | 'uploading' | 'failed';
  progress: number;
  reason?: string;
}

/** The reason of an answer that will not change by sending the same photo again. */
function rejection(answer: Extract<PhotoAnswer, { status: 'fileTooLarge' | 'invalid' }>): string {
  return answer.status === 'fileTooLarge' ? esAdmin.photos.fileTooLarge : esAdmin.photos.problems[answer.problem];
}

interface EditorProps {
  banner: AdminBanner;
  onChanged: (banner: AdminBanner) => void;
}

/** UC-32 steps 2 to 4: the texts, the photo, the preview as on the home page, and publishing or hiding it. */
function BannerEditor({ banner, onChanged }: EditorProps) {
  const id = useId();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<BannerTexts>(() => banner.texts ?? EMPTY);
  const [invalid, setInvalid] = useState<BannerField[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [photo, setPhoto] = useState<PendingPhoto | null>(null);
  // Picking the same file again needs a fresh input.
  const [inputVersion, setInputVersion] = useState(0);

  function changed(next: AdminBanner) {
    onChanged(next);
    // The storefront reads it again on its next load (UC-32).
    void queryClient.invalidateQueries({ queryKey: homeBannerKey });
  }

  async function run(action: () => Promise<Notice>) {
    setNotice(null);
    setBusy(true);
    try {
      setNotice(await action());
    } catch {
      setNotice({ kind: 'error', text: esAdmin.actionError });
    } finally {
      setBusy(false);
    }
  }

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    void run(async () => {
      const answer = await saveBannerTexts(draft);
      if (answer.status === 'invalid') {
        setInvalid(answer.fields);
        return { kind: 'error', text: messages.invalid };
      }
      setInvalid([]);
      setDraft(answer.banner.texts ?? EMPTY);
      changed(answer.banner);
      return { kind: 'ok', text: esAdmin.saved };
    });
  }

  function togglePublished() {
    void run(async () => {
      if (banner.published) {
        changed(await hideBanner());
        return { kind: 'ok', text: messages.hidden };
      }
      const answer = await publishBanner();
      if (answer.status === 'incomplete') {
        return { kind: 'error', text: messages.incomplete(answer.missing.map((part) => messages.parts[part])) };
      }
      changed(answer.banner);
      return { kind: 'ok', text: messages.publishedNotice };
    });
  }

  async function send(pending: PendingPhoto, bytes: Blob) {
    setPhoto({ ...pending, bytes, state: 'uploading', progress: 0 });
    let answer: PhotoAnswer;
    try {
      answer = await uploadBannerPhoto(bytes, uploadName(pending.file, bytes), pending.key, (progress) => {
        setPhoto((current) => (current?.key === pending.key ? { ...current, progress } : current));
      });
    } catch {
      setPhoto({ ...pending, bytes, state: 'failed', progress: 0, reason: esAdmin.photos.connectionLost });
      return;
    }
    switch (answer.status) {
      case 'uploaded':
        setPhoto(null);
        changed(answer.banner);
        setNotice({ kind: 'ok', text: esAdmin.photos.uploaded });
        break;
      case 'busy':
        setPhoto({ ...pending, bytes, state: 'failed', progress: 0, reason: esAdmin.photos.busy });
        break;
      default:
        // Sending it again changes nothing: the administrator picks another photo.
        setPhoto(null);
        setNotice({ kind: 'error', text: rejection(answer) });
    }
  }

  async function pick(file: File) {
    setNotice(null);
    setInputVersion((version) => version + 1);
    const pending: PendingPhoto = { file, key: newIdempotencyKey(), bytes: null, state: 'shrinking', progress: 0 };
    setPhoto(pending);
    await send(pending, await shrinkPhoto(file));
  }

  const isInvalid = (field: BannerField) => invalid.includes(field);
  const field = (name: BannerField, label: string, hint?: string) => {
    const describedBy = [hint === undefined ? '' : `${id}-${name}-hint`, isInvalid(name) ? `${id}-${name}-error` : '']
      .filter(Boolean)
      .join(' ');
    const props = {
      id: `${id}-${name}`,
      value: draft[name],
      maxLength: MAX_LENGTH[name],
      'aria-invalid': isInvalid(name),
      ...(describedBy === '' ? {} : { 'aria-describedby': describedBy }),
      onChange: (event: { target: { value: string } }) => {
        setDraft({ ...draft, [name]: event.target.value });
      },
    };
    return (
      <div className="field">
        <label htmlFor={`${id}-${name}`}>{label}</label>
        {name === 'body' ? <textarea rows={3} {...props} /> : <input {...props} />}
        {hint !== undefined && (
          <p id={`${id}-${name}-hint`} className="field-hint">
            {hint}
          </p>
        )}
        {isInvalid(name) && (
          <p id={`${id}-${name}-error`} className="field-error">
            {messages.invalidFields[name]}
          </p>
        )}
      </div>
    );
  };

  const uploading = photo !== null && photo.state !== 'failed';

  return (
    <>
      <p>
        <span className={banner.published ? 'admin-badge published' : 'admin-badge'}>
          {banner.published ? messages.published : messages.hiddenState}
        </span>
      </p>
      <form className="admin-form" onSubmit={submit}>
        {field('title', messages.title)}
        {field('body', messages.body)}
        {field('linkText', messages.linkText)}
        {field('linkPath', messages.linkPath, messages.linkPathHint)}
        <div className="admin-actions">
          <button type="submit" className="btn" disabled={busy}>
            {esAdmin.save}
          </button>
        </div>
      </form>
      <section className="admin-section" aria-labelledby={`${id}-photo`}>
        <h2 id={`${id}-photo`}>{messages.photoTitle}</h2>
        {banner.image === null && <p>{messages.noPhoto}</p>}
        <div className="field">
          <label htmlFor={`${id}-file`}>{banner.image === null ? messages.addPhoto : messages.replacePhoto}</label>
          <input
            key={inputVersion}
            id={`${id}-file`}
            type="file"
            accept={ACCEPTED_TYPES}
            disabled={uploading}
            aria-describedby={`${id}-file-hint`}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file !== undefined) {
                void pick(file);
              }
            }}
          />
          <p id={`${id}-file-hint`} className="field-hint">
            {messages.photoHint}
          </p>
        </div>
        {photo !== null && (
          <div className={`upload-row ${photo.state}`}>
            <div className="upload-head">
              <span className="upload-name">{photo.file.name}</span>
              <span className="admin-card-meta">{esAdmin.photos.states[photo.state]}</span>
            </div>
            <progress max={1} value={photo.progress} aria-label={esAdmin.photos.progress(photo.file.name)} />
            {photo.reason !== undefined && (
              <p className="form-error" role="alert">
                {photo.reason}
              </p>
            )}
            {photo.state === 'failed' && photo.bytes !== null && (
              <div className="admin-actions">
                <button
                  type="button"
                  className="btn"
                  onClick={() => {
                    const { bytes } = photo;
                    if (bytes !== null) {
                      void send(photo, bytes);
                    }
                  }}
                >
                  {esAdmin.photos.retry}
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => {
                    setPhoto(null);
                  }}
                >
                  {esAdmin.photos.dismissShort}
                </button>
              </div>
            )}
          </div>
        )}
      </section>
      <section className="admin-section" aria-labelledby={`${id}-preview`}>
        <h2 id={`${id}-preview`}>{messages.previewTitle}</h2>
        <CharacterBannerView
          preview
          banner={{
            title: draft.title.trim() === '' ? messages.previewNoTitle : draft.title,
            body: draft.body.trim(),
            linkText: draft.linkText.trim() === '' ? messages.previewNoLink : draft.linkText,
            linkPath: draft.linkPath,
            image: banner.image,
          }}
        />
      </section>
      <Feedback notice={notice} />
      <div className="admin-actions">
        <button
          type="button"
          className={banner.published ? 'btn btn-secondary' : 'btn'}
          disabled={busy}
          onClick={togglePublished}
        >
          {banner.published ? messages.hide : messages.publish}
        </button>
      </div>
      <p className="field-hint">{messages.publishHint}</p>
    </>
  );
}

/** UC-32 at /admin/portada: the character banner of the home page. */
export function HomeBannerPage() {
  const queryClient = useQueryClient();
  const found = useQuery({ queryKey: bannerKey, queryFn: ({ signal }) => fetchBanner(signal) });

  return (
    <>
      <title>{`${messages.pageTitle} | ${esAdmin.title}`}</title>
      <h1>{messages.pageTitle}</h1>
      <p className="field-hint">{messages.intro}</p>
      {found.isPending && <p role="status">{esAdmin.loading}</p>}
      {found.isError && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void found.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      )}
      {found.data !== undefined && (
        <BannerEditor
          banner={found.data}
          onChanged={(banner) => {
            queryClient.setQueryData(bannerKey, banner);
          }}
        />
      )}
    </>
  );
}
