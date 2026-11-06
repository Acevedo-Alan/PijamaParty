import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type SubmitEvent } from 'react';
import { Link } from 'react-router';
import { Button } from '../../components/ui/Button.tsx';
import { Field, FieldInput, FieldLabel } from '../../components/ui/Field.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import {
  createCategory,
  deleteCategory,
  renameCategory,
  reorderCategories,
  setCategoryActive,
  type Category,
  type DeleteAnswer,
  type NameAnswer,
} from './categoriesApi.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import { CATEGORIES_KEY, useCategories } from './useCategories.ts';

const messages = esAdmin.categoryList;

/** The message of a name the server refused; null when it was saved. */
function nameProblem(answer: NameAnswer): string | null {
  switch (answer.status) {
    case 'saved':
      return null;
    case 'slugAlreadyUsed':
      return messages.slugUsed(answer.slug);
    case 'invalidName':
      return messages.invalidName;
    case 'notFound':
      return messages.notFound;
  }
}

function deleteProblem(answer: DeleteAnswer): string | null {
  switch (answer.status) {
    case 'deleted':
      return null;
    case 'hasProducts':
      return messages.hasProducts;
    case 'notFound':
      return messages.notFound;
  }
}

/** The list with one category moved one place up (-1) or down (+1). */
function moved(categories: Category[], index: number, step: -1 | 1): string[] {
  const ids = categories.map((category) => category.id);
  const [id] = ids.splice(index, 1);
  if (id !== undefined) {
    ids.splice(index + step, 0, id);
  }
  return ids;
}

interface RowProps {
  category: Category;
  index: number;
  count: number;
  busy: boolean;
  onRename: (name: string) => Promise<boolean>;
  onMove: (step: -1 | 1) => void;
  onToggleActive: () => void;
  onDelete: () => void;
}

/** Where the focus goes once the row is quiet again: it would otherwise stay on a button that disappeared or got disabled. */
type FocusTarget = 'input' | 'rename' | 'up' | 'down';

function CategoryRow({ category, index, count, busy, onRename, onMove, onToggleActive, onDelete }: RowProps) {
  const [draft, setDraft] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const rename = useRef<HTMLButtonElement>(null);
  const up = useRef<HTMLButtonElement>(null);
  const down = useRef<HTMLButtonElement>(null);
  const focusNext = useRef<FocusTarget | null>(null);

  // T-E8-24: opening "Renombrar" focuses its field, closing it returns to the button, and a move keeps the focus on
  // the arrow that was pressed (or the other one at the end of the list), once the row is enabled again.
  useEffect(() => {
    if (focusNext.current === null || busy) {
      return;
    }
    const target = { input, rename, up, down }[focusNext.current].current;
    if (target !== null && !target.disabled) {
      target.focus();
      focusNext.current = null;
    }
  }, [busy, index, draft]);

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (draft !== null && (await onRename(draft))) {
      focusNext.current = 'rename';
      setDraft(null);
    }
  }

  function move(step: -1 | 1) {
    const last = index + step === (step === -1 ? 0 : count - 1);
    focusNext.current = step === -1 ? (last ? 'down' : 'up') : last ? 'up' : 'down';
    onMove(step);
  }

  return (
    <li className="admin-card">
      {draft === null ? (
        <div className="admin-card-head">
          <p className="admin-card-title">{category.name}</p>
          <p className="admin-card-meta">/categoria/{category.slug}</p>
          {!category.active && <span className="admin-badge">{messages.inactive}</span>}
        </div>
      ) : (
        <form className="inline-form" onSubmit={(event) => void submit(event)}>
          <Field className="flex-[1_1_200px]">
            <FieldLabel>{messages.renameLabel(category.name)}</FieldLabel>
            <FieldInput
              ref={input}
              name="name"
              autoComplete="off"
              value={draft}
              maxLength={80}
              required
              onChange={(event) => {
                setDraft(event.target.value);
              }}
            />
          </Field>
          <div className="admin-actions">
            <Button type="submit" variant="primary" disabled={busy}>
              {esAdmin.save}
            </Button>
            <Button
              onClick={() => {
                focusNext.current = 'rename';
                setDraft(null);
              }}
            >
              {esAdmin.cancel}
            </Button>
          </div>
        </form>
      )}
      {draft === null && (
        <div className="admin-actions">
          <Button
            ref={rename}
            disabled={busy}
            onClick={() => {
              focusNext.current = 'input';
              setDraft(category.name);
            }}
          >
            {messages.rename}
          </Button>
          <Button
            ref={up}
            size="icon"
            aria-label={messages.moveUp(category.name)}
            disabled={busy || index === 0}
            onClick={() => {
              move(-1);
            }}
          >
            ↑
          </Button>
          <Button
            ref={down}
            size="icon"
            aria-label={messages.moveDown(category.name)}
            disabled={busy || index === count - 1}
            onClick={() => {
              move(1);
            }}
          >
            ↓
          </Button>
          <Button asChild>
            <Link to={`/admin/categorias/${category.id}/guia-de-talles`} aria-label={messages.sizeGuideOf(category.name)}>
              {messages.sizeGuide}
            </Link>
          </Button>
          <Button disabled={busy} onClick={onToggleActive}>
            {category.active ? messages.deactivate : messages.activate}
          </Button>
          <Button variant="danger" disabled={busy} onClick={onDelete}>
            {messages.delete}
          </Button>
        </div>
      )}
    </li>
  );
}

/**
 * UC-05 at /admin/categorias: create, rename, reorder, deactivate, reactivate and delete. A category with products
 * cannot be deleted; the message offers to deactivate it.
 */
export function CategoriesPage() {
  const categories = useCategories();
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);

  // Every change runs one at a time and reloads the list, which the server orders.
  const change = useMutation({
    mutationFn: (action: () => Promise<string | null>) => action(),
    onSuccess: (problem) => {
      setNotice(problem === null ? { kind: 'ok', text: esAdmin.saved } : { kind: 'error', text: problem });
    },
    onError: () => {
      setNotice({ kind: 'error', text: esAdmin.actionError });
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY }),
  });

  async function run(action: () => Promise<string | null>): Promise<boolean> {
    setNotice(null);
    try {
      return (await change.mutateAsync(action)) === null;
    } catch {
      return false;
    }
  }

  async function create(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (await run(async () => nameProblem(await createCategory(name)))) {
      setName('');
    }
  }

  return (
    <>
      <title>{`${messages.title} | ${esAdmin.title}`}</title>
      <h1>{messages.title}</h1>
      <form className="admin-form inline-form" onSubmit={(event) => void create(event)}>
        <Field className="flex-[1_1_200px]">
          <FieldLabel>{messages.newName}</FieldLabel>
          <FieldInput
            name="name"
            autoComplete="off"
            value={name}
            maxLength={80}
            required
            onChange={(event) => {
              setName(event.target.value);
            }}
          />
        </Field>
        <Button type="submit" variant="primary" disabled={change.isPending}>
          {messages.create}
        </Button>
      </form>
      <Feedback notice={notice} />
      {categories.isPending && <p role="status">{esAdmin.loading}</p>}
      {categories.isError && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <Button onClick={() => void categories.refetch()}>{esAdmin.retry}</Button>
        </div>
      )}
      {categories.data?.length === 0 && <p>{messages.empty}</p>}
      {categories.data !== undefined && categories.data.length > 0 && (
        <ol className="admin-list">
          {categories.data.map((category, index, all) => (
            <CategoryRow
              key={category.id}
              category={category}
              index={index}
              count={all.length}
              busy={change.isPending}
              onRename={(newName) => run(async () => nameProblem(await renameCategory(category.id, newName)))}
              onMove={(step) => {
                void run(async () => {
                  queryClient.setQueryData(CATEGORIES_KEY, await reorderCategories(moved(all, index, step)));
                  return null;
                });
              }}
              onToggleActive={() => {
                void run(async () =>
                  (await setCategoryActive(category.id, !category.active)) ? null : messages.notFound,
                );
              }}
              onDelete={() => {
                if (window.confirm(messages.confirmDelete(category.name))) {
                  void run(async () => deleteProblem(await deleteCategory(category.id)));
                }
              }}
            />
          ))}
        </ol>
      )}
    </>
  );
}
