import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn, problemText } from './adminSession.ts';

type CategoryResponse = components['schemas']['CategoryResponse'];

export interface Category {
  id: string;
  name: string;
  slug: string;
  active: boolean;
}

/** The answer of creating or renaming a category (UC-05). */
export type NameAnswer =
  | { status: 'saved' }
  | { status: 'slugAlreadyUsed'; slug: string }
  | { status: 'invalidName' }
  | { status: 'notFound' };

export type DeleteAnswer = { status: 'deleted' } | { status: 'hasProducts' } | { status: 'notFound' };

function toCategory({ id, name, slug, active }: CategoryResponse): Category {
  if (id === undefined || name === undefined || slug === undefined) {
    throw new Error('Incomplete category');
  }
  return { id, name, slug, active: active === true };
}

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

/** Every category, active or not, by position. */
export async function fetchCategories(signal?: AbortSignal): Promise<Category[]> {
  const { data, response } = await api.GET('/api/admin/catalog/categories', { signal: signal ?? null });
  ensureSignedIn(response);
  if (data === undefined) {
    throw failed('Category list', response);
  }
  return data.map(toCategory);
}

function nameAnswer(error: unknown, response: Response, action: string): NameAnswer {
  ensureSignedIn(response);
  switch (response.status) {
    case 404:
      return { status: 'notFound' };
    case 409:
      return { status: 'slugAlreadyUsed', slug: problemText(error, 'slug') };
    case 422:
      return { status: 'invalidName' };
    default:
      if (!response.ok) {
        throw failed(action, response);
      }
      return { status: 'saved' };
  }
}

export async function createCategory(name: string): Promise<NameAnswer> {
  const { error, response } = await api.POST('/api/admin/catalog/categories', { body: { name } });
  return nameAnswer(error, response, 'Category creation');
}

export async function renameCategory(id: string, name: string): Promise<NameAnswer> {
  const { error, response } = await api.PATCH('/api/admin/catalog/categories/{id}', {
    params: { path: { id } },
    body: { name },
  });
  return nameAnswer(error, response, 'Category rename');
}

/** Sends every category id in the new order; the server answers the reordered list. */
export async function reorderCategories(categoryIds: string[]): Promise<Category[]> {
  const { data, response } = await api.PUT('/api/admin/catalog/categories/order', { body: { categoryIds } });
  ensureSignedIn(response);
  if (data === undefined) {
    throw failed('Category reorder', response);
  }
  return data.map(toCategory);
}

/** Deactivates or reactivates a category; false when it no longer exists. */
export async function setCategoryActive(id: string, active: boolean): Promise<boolean> {
  const options = { params: { path: { id } } };
  const { response } = active
    ? await api.POST('/api/admin/catalog/categories/{id}/activate', options)
    : await api.POST('/api/admin/catalog/categories/{id}/deactivate', options);
  ensureSignedIn(response);
  if (response.status === 404) {
    return false;
  }
  if (!response.ok) {
    throw failed('Category state change', response);
  }
  return true;
}

export async function deleteCategory(id: string): Promise<DeleteAnswer> {
  const { response } = await api.DELETE('/api/admin/catalog/categories/{id}', { params: { path: { id } } });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (response.status === 409) {
    return { status: 'hasProducts' };
  }
  if (!response.ok) {
    throw failed('Category deletion', response);
  }
  return { status: 'deleted' };
}
