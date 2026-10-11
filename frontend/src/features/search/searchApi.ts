import { api } from '../../api/client.ts';
import { toImage, type ListingImage } from '../catalog/catalogApi.ts';

/** A product suggested while the visitor types (UC-26). */
export interface Suggestion {
  slug: string;
  name: string;
  image: ListingImage | null;
}

export interface ShopCategory {
  slug: string;
  name: string;
}

/** Up to 5 listed products for a text of at least 2 characters (the server answers none for less). */
export async function fetchSuggestions(text: string, signal?: AbortSignal): Promise<Suggestion[]> {
  const { data, response } = await api.GET('/api/catalog/suggestions', {
    params: { query: { q: text } },
    signal: signal ?? null,
  });
  if (data === undefined) {
    throw new Error(`Suggestions failed with status ${String(response.status)}`);
  }
  return (data.items ?? []).flatMap(({ slug, name, image }) =>
    slug === undefined || name === undefined ? [] : [{ slug, name, image: toImage(image) }],
  );
}

/** The active categories by position, for the links shown when a search finds nothing. */
export async function fetchShopCategories(signal?: AbortSignal): Promise<ShopCategory[]> {
  const { data, response } = await api.GET('/api/catalog/categories', { signal: signal ?? null });
  if (data === undefined) {
    throw new Error(`Categories failed with status ${String(response.status)}`);
  }
  return (data.items ?? []).flatMap(({ slug, name }) =>
    slug === undefined || name === undefined ? [] : [{ slug, name }],
  );
}
