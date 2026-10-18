import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type SubmitEvent } from 'react';
import { Link, useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import {
  deleteSizeGuide,
  fetchSizeGuide,
  saveSizeGuide,
  type CategoryGuideAnswer,
  type SizeGuide,
  type SizeGuideField,
} from './sizeGuideApi.ts';

const messages = es.admin.sizeGuidePage;

/** The limits of RNF-31.5, so the form never offers more than the server takes. */
const MAX_COLUMNS = 6;
const MAX_ROWS = 20;

const sizeGuideKey = (categoryId: string) => ['admin', 'sizeGuide', categoryId] as const;

/** Columns and rows carry a key of the form's own, so a row keeps its fields when it moves. */
interface Column {
  key: number;
  name: string;
}

interface Row {
  key: number;
  size: string;
  /** By column key, so removing a column needs no change to the rows. */
  values: Record<number, string>;
}

interface Draft {
  columns: Column[];
  rows: Row[];
  note: string;
}

/** The stored guide as the form edits it; a new guide starts with one column and one size to fill. */
function toDraft(guide: SizeGuide | null): Draft {
  if (guide === null) {
    return { columns: [{ key: 0, name: '' }], rows: [{ key: 0, size: '', values: {} }], note: '' };
  }
  const columns = guide.columns.map((name, index) => ({ key: index, name }));
  return {
    columns,
    rows: guide.rows.map((row, index) => ({
      key: index,
      size: row.size,
      values: Object.fromEntries(columns.map((column, index) => [column.key, row.values[index] ?? ''])),
    })),
    note: guide.note ?? '',
  };
}

/** A key none of the items uses yet. */
function newKey(items: { key: number }[]): number {
  return Math.max(-1, ...items.map((item) => item.key)) + 1;
}

function toGuide({ columns, rows, note }: Draft): SizeGuide {
  return {
    columns: columns.map((column) => column.name),
    rows: rows.map((row) => ({ size: row.size, values: columns.map((column) => row.values[column.key] ?? '') })),
    note: note.trim() === '' ? null : note,
  };
}

interface FormProps {
  categoryId: string;
  guide: SizeGuide | null;
  onChanged: (guide: SizeGuide | null) => void;
}

/** The whole guide in one form: column names, one row per size, and the note. Saving replaces the stored guide. */
function SizeGuideForm({ categoryId, guide, onChanged }: FormProps) {
  const id = useId();
  const [draft, setDraft] = useState(() => toDraft(guide));
  const [invalid, setInvalid] = useState<SizeGuideField[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const { columns, rows, note } = draft;

  function load(next: SizeGuide | null) {
    setDraft(toDraft(next));
    setInvalid([]);
  }

  function setColumn(key: number, name: string) {
    setDraft({ ...draft, columns: columns.map((column) => (column.key === key ? { key, name } : column)) });
  }

  function setRow(key: number, change: Partial<Row>) {
    setDraft({ ...draft, rows: rows.map((row) => (row.key === key ? { ...row, ...change } : row)) });
  }

  function moveRow(index: number, step: -1 | 1) {
    const next = [...rows];
    const [row] = next.splice(index, 1);
    if (row !== undefined) {
      next.splice(index + step, 0, row);
    }
    setDraft({ ...draft, rows: next });
  }

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice(null);
    setBusy(true);
    try {
      const answer = await saveSizeGuide(categoryId, toGuide(draft));
      switch (answer.status) {
        case 'saved':
          load(answer.guide);
          setNotice({ kind: 'ok', text: es.admin.saved });
          onChanged(answer.guide);
          break;
        case 'invalid':
          setInvalid(answer.fields);
          setNotice({ kind: 'error', text: messages.invalid });
          break;
        case 'notFound':
          setNotice({ kind: 'error', text: messages.notFound });
          break;
      }
    } catch {
      setNotice({ kind: 'error', text: es.admin.actionError });
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!window.confirm(messages.confirmDelete)) {
      return;
    }
    setNotice(null);
    setBusy(true);
    try {
      if (await deleteSizeGuide(categoryId)) {
        load(null);
        setNotice({ kind: 'ok', text: messages.deleted });
        onChanged(null);
      } else {
        setNotice({ kind: 'error', text: messages.notFound });
      }
    } catch {
      setNotice({ kind: 'error', text: es.admin.actionError });
    } finally {
      setBusy(false);
    }
  }

  /** What labels a column's values: its name, or its place while it has none. */
  function columnName(index: number): string {
    const name = columns[index]?.name.trim() ?? '';
    return name === '' ? messages.columnLabel(index + 1) : name;
  }

  const isInvalid = (field: SizeGuideField) => invalid.includes(field);
  const errorOf = (field: SizeGuideField) => (isInvalid(field) ? { 'aria-describedby': `${id}-${field}-error` } : {});

  return (
    <form className="admin-form" onSubmit={(event) => void submit(event)}>
      <p className="field-hint">{messages.hint}</p>
      <fieldset className="admin-section size-guide-fieldset" {...errorOf('columns')}>
        <legend>{messages.columnsTitle}</legend>
        {columns.map((column, index) => (
          <div className="inline-form" key={column.key}>
            <label className="field">
              <span>{messages.columnLabel(index + 1)}</span>
              <input
                value={column.name}
                maxLength={30}
                aria-invalid={isInvalid('columns')}
                onChange={(event) => {
                  setColumn(column.key, event.target.value);
                }}
              />
            </label>
            <button
              type="button"
              className="btn btn-secondary"
              aria-label={messages.removeColumn(columnName(index))}
              disabled={busy || columns.length === 1}
              onClick={() => {
                // Its values go too, so a column added later under the same key starts empty.
                setDraft({
                  ...draft,
                  columns: columns.filter((other) => other.key !== column.key),
                  rows: rows.map((row) => ({
                    ...row,
                    values: Object.fromEntries(
                      Object.entries(row.values).filter(([key]) => Number(key) !== column.key),
                    ),
                  })),
                });
              }}
            >
              {messages.remove}
            </button>
          </div>
        ))}
        {isInvalid('columns') && (
          <p id={`${id}-columns-error`} className="field-error">
            {messages.invalidColumns}
          </p>
        )}
        <div className="admin-actions">
          <button
            type="button"
            className="btn btn-secondary"
            disabled={busy || columns.length >= MAX_COLUMNS}
            onClick={() => {
              setDraft({ ...draft, columns: [...columns, { key: newKey(columns), name: '' }] });
            }}
          >
            {messages.addColumn}
          </button>
        </div>
      </fieldset>
      <fieldset className="admin-section size-guide-fieldset" {...errorOf('rows')}>
        <legend>{messages.rowsTitle}</legend>
        <ol className="admin-list">
          {rows.map((row, index) => (
            <li className="admin-card" key={row.key}>
              <fieldset className="size-guide-fieldset">
                <legend>{messages.rowTitle(index + 1)}</legend>
                <label className="field">
                  <span>{messages.sizeLabel}</span>
                  <input
                    value={row.size}
                    maxLength={20}
                    aria-invalid={isInvalid('rows')}
                    onChange={(event) => {
                      setRow(row.key, { size: event.target.value });
                    }}
                  />
                </label>
                {columns.map((column, columnIndex) => (
                  <label className="field" key={column.key}>
                    <span>{columnName(columnIndex)}</span>
                    <input
                      value={row.values[column.key] ?? ''}
                      maxLength={20}
                      aria-invalid={isInvalid('rows')}
                      onChange={(event) => {
                        setRow(row.key, { values: { ...row.values, [column.key]: event.target.value } });
                      }}
                    />
                  </label>
                ))}
              </fieldset>
              <div className="admin-actions">
                <button
                  type="button"
                  className="btn btn-secondary icon-text"
                  aria-label={messages.moveUp(index + 1)}
                  disabled={busy || index === 0}
                  onClick={() => {
                    moveRow(index, -1);
                  }}
                >
                  ↑
                </button>
                <button
                  type="button"
                  className="btn btn-secondary icon-text"
                  aria-label={messages.moveDown(index + 1)}
                  disabled={busy || index === rows.length - 1}
                  onClick={() => {
                    moveRow(index, 1);
                  }}
                >
                  ↓
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  aria-label={messages.removeRow(index + 1)}
                  disabled={busy || rows.length === 1}
                  onClick={() => {
                    setDraft({ ...draft, rows: rows.filter((other) => other.key !== row.key) });
                  }}
                >
                  {messages.remove}
                </button>
              </div>
            </li>
          ))}
        </ol>
        {isInvalid('rows') && (
          <p id={`${id}-rows-error`} className="field-error">
            {messages.invalidRows}
          </p>
        )}
        <div className="admin-actions">
          <button
            type="button"
            className="btn btn-secondary"
            disabled={busy || rows.length >= MAX_ROWS}
            onClick={() => {
              setDraft({ ...draft, rows: [...rows, { key: newKey(rows), size: '', values: {} }] });
            }}
          >
            {messages.addRow}
          </button>
        </div>
      </fieldset>
      <div className="field">
        <label htmlFor={`${id}-note`}>{messages.note}</label>
        <textarea
          id={`${id}-note`}
          value={note}
          rows={3}
          maxLength={500}
          aria-invalid={isInvalid('note')}
          aria-describedby={isInvalid('note') ? `${id}-note-hint ${id}-note-error` : `${id}-note-hint`}
          onChange={(event) => {
            setDraft({ ...draft, note: event.target.value });
          }}
        />
        <p id={`${id}-note-hint`} className="field-hint">
          {messages.noteHint}
        </p>
        {isInvalid('note') && (
          <p id={`${id}-note-error`} className="field-error">
            {messages.invalidNote}
          </p>
        )}
      </div>
      <Feedback notice={notice} />
      <div className="admin-actions">
        <button type="submit" className="btn" disabled={busy}>
          {es.admin.save}
        </button>
        {guide !== null && (
          <button type="button" className="btn btn-secondary danger" disabled={busy} onClick={() => void remove()}>
            {messages.delete}
          </button>
        )}
      </div>
    </form>
  );
}

/** RNF-31.5 at /admin/categorias/:categoryId/guia-de-talles: the size guide of one category. */
export function SizeGuidePage() {
  const { categoryId = '' } = useParams();
  const queryClient = useQueryClient();
  const found = useQuery({
    queryKey: sizeGuideKey(categoryId),
    queryFn: ({ signal }) => fetchSizeGuide(categoryId, signal),
  });
  const loaded = found.data?.status === 'found' ? found.data.category : null;

  return (
    <>
      <title>{`${messages.title} | ${es.admin.title}`}</title>
      <Link to="/admin/categorias" className="back-link">
        {messages.back}
      </Link>
      {found.isPending && <p role="status">{es.admin.loading}</p>}
      {found.isError && (
        <div role="alert">
          <p>{es.admin.loadError}</p>
          <button type="button" className="btn" onClick={() => void found.refetch()}>
            {es.admin.retry}
          </button>
        </div>
      )}
      {found.data?.status === 'notFound' && (
        <>
          <h1>{messages.title}</h1>
          <p role="alert">{messages.notFound}</p>
        </>
      )}
      {loaded !== null && (
        <>
          <h1>{messages.heading(loaded.categoryName)}</h1>
          {loaded.guide === null && <p>{messages.noGuide}</p>}
          <SizeGuideForm
            categoryId={categoryId}
            guide={loaded.guide}
            onChanged={(guide) => {
              const next: CategoryGuideAnswer = {
                status: 'found',
                category: { categoryName: loaded.categoryName, guide },
              };
              queryClient.setQueryData(sizeGuideKey(categoryId), next);
            }}
          />
        </>
      )}
    </>
  );
}
