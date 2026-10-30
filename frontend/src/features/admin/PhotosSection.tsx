import { useQueryClient } from '@tanstack/react-query';
import { useId, useRef, useState, type DragEvent } from 'react';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { newIdempotencyKey } from './adminSession.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import { deleteImage, reorderImages, uploadImage, type AdminProduct, type UploadAnswer } from './productsApi.ts';
import { shrinkPhoto, uploadName } from './shrinkPhoto.ts';
import { productKey, useStoreProduct } from './useAdminProduct.ts';

const messages = esAdmin.photos;
const ACCEPTED_TYPES = 'image/jpeg,image/png,image/webp';

type RowState = 'shrinking' | 'waiting' | 'uploading' | 'uploaded' | 'failed' | 'rejected';

/**
 * One photo picked to upload (UC-06, T-E1-22). Its key and its shrunk bytes are kept, so "Retry" sends exactly the
 * same upload and never duplicates the photo.
 */
interface UploadRow {
  id: string;
  file: File;
  color: string | null;
  key: string;
  bytes: Blob | null;
  state: RowState;
  /** Share already sent, from 0 to 1. */
  progress: number;
  /** Why it failed or was rejected. */
  reason?: string;
}

/** The reason of an answer that will not change by sending the same photo again. */
function rejection(answer: Exclude<UploadAnswer, { status: 'uploaded' } | { status: 'busy' }>): string {
  switch (answer.status) {
    case 'tooManyImages':
      return messages.tooMany;
    case 'fileTooLarge':
      return messages.fileTooLarge;
    case 'invalid':
      return messages.problems[answer.problem];
    case 'notFound':
      return esAdmin.productEdit.notFound;
  }
}

/** The new order of the photo ids after moving the one at {@code from} to {@code to}. */
export function moved(ids: readonly string[], from: number, to: number): string[] {
  const next = [...ids];
  const [id] = next.splice(from, 1);
  if (id !== undefined) {
    next.splice(to, 0, id);
  }
  return next;
}

/**
 * UC-06 step 4: the photos by position, each of one color or of the whole product, to reorder (buttons, or dragging
 * with a mouse) and delete; and the upload of several photos at once, shrunk on the device and sent one after the
 * other with their progress (T-E1-22, RNF-26).
 */
export function PhotosSection({ product }: { product: AdminProduct }) {
  const store = useStoreProduct();
  const queryClient = useQueryClient();
  const titleId = useId();
  const hintId = useId();
  const uploadsId = useId();
  const [color, setColor] = useState('');
  const [rows, setRows] = useState<UploadRow[]>([]);
  // The rows as the upload loop reads them between renders, and whether that loop is running.
  const current = useRef<UploadRow[]>([]);
  const draining = useRef(false);
  // Picking the same files again needs a fresh input.
  const [inputVersion, setInputVersion] = useState(0);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);
  const [dragged, setDragged] = useState<number | null>(null);

  function commit(next: UploadRow[]) {
    current.current = next;
    setRows(next);
  }

  function patch(id: string, change: Partial<UploadRow>) {
    commit(current.current.map((row) => (row.id === id ? { ...row, ...change } : row)));
  }

  async function drain() {
    if (draining.current) {
      return;
    }
    draining.current = true;
    try {
      for (;;) {
        const next = current.current.find((row) => row.state === 'waiting');
        if (next?.bytes === undefined || next.bytes === null) {
          return;
        }
        patch(next.id, { state: 'uploading', progress: 0 });
        let answer: UploadAnswer;
        try {
          answer = await uploadImage(product.id, next.bytes, uploadName(next.file, next.bytes), next.color, next.key,
            (progress) => {
              patch(next.id, { progress });
            });
        } catch {
          // Lost connection: the same key and bytes can be sent again without a second photo.
          patch(next.id, { state: 'failed', reason: messages.connectionLost });
          continue;
        }
        if (answer.status === 'uploaded') {
          patch(next.id, { state: 'uploaded', progress: 1 });
          await queryClient.invalidateQueries({ queryKey: productKey(product.id) });
        } else if (answer.status === 'busy') {
          patch(next.id, { state: 'failed', reason: messages.busy });
        } else {
          patch(next.id, { state: 'rejected', reason: rejection(answer) });
        }
      }
    } finally {
      draining.current = false;
    }
  }

  async function pick(files: File[]) {
    const added: UploadRow[] = files.map((file) => ({
      id: newIdempotencyKey(),
      file,
      color: color === '' ? null : color,
      key: newIdempotencyKey(),
      bytes: null,
      state: 'shrinking',
      progress: 0,
    }));
    commit([...current.current, ...added]);
    setInputVersion((version) => version + 1);
    for (const row of added) {
      const bytes = await shrinkPhoto(row.file);
      patch(row.id, { bytes, state: 'waiting' });
      void drain();
    }
  }

  function retry(id: string) {
    commit(
      current.current.map((row) => {
        if (row.id !== id) {
          return row;
        }
        const again: UploadRow = { ...row, state: 'waiting', progress: 0 };
        delete again.reason;
        return again;
      }),
    );
    void drain();
  }

  function dismiss(id: string) {
    commit(current.current.filter((row) => row.id !== id));
  }

  async function run(action: () => Promise<Notice | null>) {
    setSaving(true);
    setNotice(null);
    try {
      setNotice(await action());
    } catch {
      setNotice({ kind: 'error', text: esAdmin.actionError });
    } finally {
      setSaving(false);
    }
  }

  function reorder(from: number, to: number) {
    if (from === to) {
      return;
    }
    const ids = moved(
      product.images.map((image) => image.id),
      from,
      to,
    );
    void run(async () => {
      const reordered = await reorderImages(product.id, ids);
      if (reordered === null) {
        return { kind: 'error', text: esAdmin.productEdit.notFound };
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

  function drop(event: DragEvent<HTMLLIElement>, index: number) {
    event.preventDefault();
    if (dragged !== null) {
      reorder(dragged, index);
    }
    setDragged(null);
  }

  return (
    <section className="admin-section" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.title}</h2>
      {product.images.length === 0 ? (
        <p>{messages.empty}</p>
      ) : (
        <>
          <p className="field-hint">{messages.dragHint}</p>
          <ol className="photo-list">
            {product.images.map(({ id, color: photoColor, image }, index, all) => {
              const position = index + 1;
              return (
                <li
                  key={id}
                  className={dragged === index ? 'photo-item dragging' : 'photo-item'}
                  draggable={!saving}
                  onDragStart={() => {
                    setDragged(index);
                  }}
                  onDragEnd={() => {
                    setDragged(null);
                  }}
                  onDragOver={(event) => {
                    event.preventDefault();
                  }}
                  onDrop={(event) => {
                    drop(event, index);
                  }}
                >
                  <img
                    src={image.src}
                    alt={messages.alt(position, photoColor)}
                    width={96}
                    height={96}
                    loading="lazy"
                    draggable={false}
                    style={image.placeholder === undefined ? undefined : { backgroundImage: `url(${image.placeholder})` }}
                  />
                  <p className="admin-card-meta">{photoColor ?? messages.wholeProduct}</p>
                  <div className="admin-actions">
                    <button
                      type="button"
                      className="btn btn-secondary icon-text"
                      aria-label={messages.moveUp(position)}
                      disabled={saving || index === 0}
                      onClick={() => {
                        reorder(index, index - 1);
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
                        reorder(index, index + 1);
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
                      {esAdmin.categoryList.delete}
                    </button>
                  </div>
                </li>
              );
            })}
          </ol>
        </>
      )}
      <Feedback notice={notice} />
      <div className="admin-form">
        <label className="field">
          <span>{messages.color}</span>
          <select
            value={color}
            onChange={(event) => {
              setColor(event.target.value);
            }}
          >
            <option value="">{messages.wholeProduct}</option>
            {product.colors.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>{messages.file}</span>
          <input
            key={inputVersion}
            type="file"
            multiple
            accept={ACCEPTED_TYPES}
            aria-describedby={hintId}
            onChange={(event) => {
              const files = [...(event.target.files ?? [])];
              if (files.length > 0) {
                void pick(files);
              }
            }}
          />
        </label>
        <p id={hintId} className="field-hint">
          {messages.fileHint}
        </p>
      </div>
      {rows.length > 0 && (
        <>
        <h3 id={uploadsId} className="visually-hidden">
          {messages.uploads}
        </h3>
        <ul className="upload-list" aria-labelledby={uploadsId}>
          {rows.map((row) => (
            <li key={row.id} className={`upload-row ${row.state}`}>
              <div className="upload-head">
                <span className="upload-name">{row.file.name}</span>
                <span className="admin-card-meta">{messages.states[row.state]}</span>
              </div>
              <progress
                max={1}
                value={row.state === 'uploaded' ? 1 : row.progress}
                aria-label={messages.progress(row.file.name)}
              />
              {row.reason !== undefined && (
                <p className="form-error" role="alert">
                  {row.reason}
                </p>
              )}
              {(row.state === 'failed' || row.state === 'rejected' || row.state === 'uploaded') && (
                <div className="admin-actions">
                  {row.state === 'failed' && (
                    <button
                      type="button"
                      className="btn"
                      aria-label={messages.retryOne(row.file.name)}
                      onClick={() => {
                        retry(row.id);
                      }}
                    >
                      {messages.retry}
                    </button>
                  )}
                  <button
                    type="button"
                    className="btn btn-secondary"
                    aria-label={messages.dismiss(row.file.name)}
                    onClick={() => {
                      dismiss(row.id);
                    }}
                  >
                    {messages.dismissShort}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
        </>
      )}
    </section>
  );
}
