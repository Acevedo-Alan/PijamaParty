import { useQueryClient } from '@tanstack/react-query';
import { useId, useState, type SubmitEvent } from 'react';
import { es } from '../../i18n/es.ts';
import { newIdempotencyKey } from './adminSession.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import { deleteImage, reorderImages, uploadImage, type AdminProduct, type UploadAnswer } from './productsApi.ts';
import { productKey, useStoreProduct } from './useAdminProduct.ts';

const messages = es.admin.photos;
const ACCEPTED_TYPES = 'image/jpeg,image/png,image/webp';

function uploadNotice(answer: UploadAnswer): Notice {
  switch (answer.status) {
    case 'uploaded':
      return { kind: 'ok', text: messages.uploaded };
    case 'tooManyImages':
      return { kind: 'error', text: messages.tooMany };
    case 'fileTooLarge':
      return { kind: 'error', text: messages.fileTooLarge };
    case 'invalid':
      return { kind: 'error', text: messages.problems[answer.problem] };
    case 'notFound':
      return { kind: 'error', text: es.admin.productEdit.notFound };
  }
}

/** One photo chosen to upload, with the key its retries repeat (UC-06): a new file or color gets a new key. */
interface Upload {
  file: File | null;
  color: string;
  key: string;
}

/**
 * UC-06 step 4: the photos by position, each of one color or of the whole product, to reorder and delete; and the
 * upload of one photo at a time. Several files, shrinking on the device and progress come in T-E1-22.
 */
export function PhotosSection({ product }: { product: AdminProduct }) {
  const store = useStoreProduct();
  const queryClient = useQueryClient();
  const titleId = useId();
  const [upload, setUpload] = useState<Upload>(() => ({ file: null, color: '', key: newIdempotencyKey() }));
  const [connectionLost, setConnectionLost] = useState(false);
  // Choosing the same file again after an upload needs a fresh input.
  const [inputVersion, setInputVersion] = useState(0);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);

  async function run(action: () => Promise<Notice | null>) {
    setSaving(true);
    setNotice(null);
    try {
      setNotice(await action());
    } catch {
      setNotice({ kind: 'error', text: es.admin.actionError });
    } finally {
      setSaving(false);
    }
  }

  function choose(next: Partial<Omit<Upload, 'key'>>) {
    setUpload({ ...upload, ...next, key: newIdempotencyKey() });
    setConnectionLost(false);
    setNotice(null);
  }

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const { file, color, key } = upload;
    if (file === null) {
      return;
    }
    setUploading(true);
    setNotice(null);
    let answer: UploadAnswer;
    try {
      answer = await uploadImage(product.id, file, color === '' ? null : color, key);
    } catch {
      // Lost connection: the same key and file can be sent again without a second photo.
      setConnectionLost(true);
      setNotice({ kind: 'error', text: messages.connectionLost });
      setUploading(false);
      return;
    }
    setConnectionLost(false);
    setNotice(uploadNotice(answer));
    if (answer.status === 'uploaded') {
      setUpload({ file: null, color, key: newIdempotencyKey() });
      setInputVersion(inputVersion + 1);
      await queryClient.invalidateQueries({ queryKey: productKey(product.id) });
    }
    setUploading(false);
  }

  function move(index: number, step: -1 | 1) {
    const ids = product.images.map((image) => image.id);
    const [id] = ids.splice(index, 1);
    if (id === undefined) {
      return;
    }
    ids.splice(index + step, 0, id);
    void run(async () => {
      const reordered = await reorderImages(product.id, ids);
      if (reordered === null) {
        return { kind: 'error', text: es.admin.productEdit.notFound };
      }
      store(reordered);
      return null;
    });
  }

  function remove(imageId: string, position: number) {
    if (!window.confirm(messages.confirmDelete(position))) {
      return;
    }
    void run(async () => {
      const deleted = await deleteImage(product.id, imageId);
      await queryClient.invalidateQueries({ queryKey: productKey(product.id) });
      return deleted ? null : { kind: 'error', text: messages.notFound };
    });
  }

  return (
    <section className="admin-section" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.title}</h2>
      {product.images.length === 0 ? (
        <p>{messages.empty}</p>
      ) : (
        <ol className="photo-list">
          {product.images.map(({ id, color, image }, index, all) => {
            const position = index + 1;
            return (
              <li key={id} className="photo-item">
                <img
                  src={image.src}
                  alt={messages.alt(position, color)}
                  width={96}
                  height={96}
                  loading="lazy"
                  style={image.placeholder === undefined ? undefined : { backgroundImage: `url(${image.placeholder})` }}
                />
                <p className="admin-card-meta">{color ?? messages.wholeProduct}</p>
                <div className="admin-actions">
                  <button
                    type="button"
                    className="btn btn-secondary icon-text"
                    aria-label={messages.moveUp(position)}
                    disabled={saving || index === 0}
                    onClick={() => {
                      move(index, -1);
                    }}
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary icon-text"
                    aria-label={messages.moveDown(position)}
                    disabled={saving || index === all.length - 1}
                    onClick={() => {
                      move(index, 1);
                    }}
                  >
                    ↓
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary danger"
                    aria-label={messages.delete(position)}
                    disabled={saving}
                    onClick={() => {
                      remove(id, position);
                    }}
                  >
                    {es.admin.categoryList.delete}
                  </button>
                </div>
              </li>
            );
          })}
        </ol>
      )}
      <form className="admin-form" onSubmit={(event) => void submit(event)}>
        <label className="field">
          <span>{messages.file}</span>
          <input
            key={inputVersion}
            type="file"
            accept={ACCEPTED_TYPES}
            onChange={(event) => {
              choose({ file: event.target.files?.[0] ?? null });
            }}
          />
        </label>
        <label className="field">
          <span>{messages.color}</span>
          <select
            value={upload.color}
            onChange={(event) => {
              choose({ color: event.target.value });
            }}
          >
            <option value="">{messages.wholeProduct}</option>
            {product.colors.map((color) => (
              <option key={color} value={color}>
                {color}
              </option>
            ))}
          </select>
        </label>
        <Feedback notice={notice} />
        <button type="submit" className="btn" disabled={uploading || upload.file === null}>
          {uploading ? messages.uploading : connectionLost ? messages.retry : messages.upload}
        </button>
      </form>
    </section>
  );
}
