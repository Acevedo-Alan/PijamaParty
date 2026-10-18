import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn, problemProperty } from './adminSession.ts';

type GuideResponse = components['schemas']['AdminSizeGuideResponse'];

/** A category's size guide (RNF-31.5): the columns, one row per size with one value per column, and the note. */
export interface SizeGuide {
  columns: string[];
  rows: { size: string; values: string[] }[];
  note: string | null;
}

/** The category's name and its guide, null without one. */
export interface CategoryGuide {
  categoryName: string;
  guide: SizeGuide | null;
}

/** The parts of INVALID_SIZE_GUIDE, in the request's own names. */
export const SIZE_GUIDE_FIELDS = ['columns', 'rows', 'note'] as const;
export type SizeGuideField = (typeof SIZE_GUIDE_FIELDS)[number];

export type CategoryGuideAnswer = { status: 'found'; category: CategoryGuide } | { status: 'notFound' };

export type SaveAnswer =
  | { status: 'saved'; guide: SizeGuide }
  | { status: 'invalid'; fields: SizeGuideField[] }
  | { status: 'notFound' };

function toGuide({ columns, rows, note }: GuideResponse): SizeGuide {
  if (columns === undefined || rows === undefined) {
    throw new Error('Incomplete size guide');
  }
  return {
    columns,
    rows: rows.map(({ size, values }) => {
      if (size === undefined || values === undefined) {
        throw new Error('Incomplete size guide row');
      }
      return { size, values };
    }),
    note: note ?? null,
  };
}

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

export async function fetchSizeGuide(categoryId: string, signal?: AbortSignal): Promise<CategoryGuideAnswer> {
  const { data, response } = await api.GET('/api/admin/catalog/categories/{id}/size-guide', {
    params: { path: { id: categoryId } },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  const name = data?.category?.name;
  if (data === undefined || name === undefined) {
    throw failed('Size guide', response);
  }
  return { status: 'found', category: { categoryName: name, guide: data.guide ? toGuide(data.guide) : null } };
}

/** Replaces the whole guide; the server trims every text. */
export async function saveSizeGuide(categoryId: string, guide: SizeGuide): Promise<SaveAnswer> {
  const { data, error, response } = await api.PUT('/api/admin/catalog/categories/{id}/size-guide', {
    params: { path: { id: categoryId } },
    // A guide without a note leaves it out: the request takes no null note.
    body: { columns: guide.columns, rows: guide.rows, ...(guide.note === null ? {} : { note: guide.note }) },
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (response.status === 422) {
    const names = problemProperty(error, 'fields');
    return {
      status: 'invalid',
      fields: Array.isArray(names) ? SIZE_GUIDE_FIELDS.filter((field) => names.includes(field)) : [],
    };
  }
  if (data === undefined) {
    throw failed('Size guide save', response);
  }
  return { status: 'saved', guide: toGuide(data) };
}

/** Deletes the guide; false when the category no longer exists. */
export async function deleteSizeGuide(categoryId: string): Promise<boolean> {
  const { response } = await api.DELETE('/api/admin/catalog/categories/{id}/size-guide', {
    params: { path: { id: categoryId } },
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return false;
  }
  if (!response.ok) {
    throw failed('Size guide deletion', response);
  }
  return true;
}
