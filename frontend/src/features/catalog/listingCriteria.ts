import type { components } from '../../api/schema.d.ts';

type FilterOptionsResponse = components['schemas']['FilterOptionsResponse'];

export type Season = NonNullable<FilterOptionsResponse['seasons']>[number];
export type Audience = NonNullable<FilterOptionsResponse['audiences']>[number];
/** RELEVANCE only applies to a search (UC-26), where it is the default; NEWEST is the default otherwise. */
export type ProductSort = 'RELEVANCE' | 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC';

/** The filters of UC-02. Prices are in cents and bound the "from" price, both included. */
export interface ProductFilters {
  sizes: string[];
  colors: string[];
  materials: string[];
  seasons: Season[];
  audiences: Audience[];
  minPriceCents?: number;
  maxPriceCents?: number;
}

export interface ListingCriteria {
  filters: ProductFilters;
  sort: ProductSort;
  /** What the visitor searches for (UC-26), trimmed; absent outside a search. */
  text?: string;
}

/** The sort applied when none is chosen: by relevance for a search, newest first otherwise. */
export function defaultSort(criteria: { text?: string | undefined }): ProductSort {
  return criteria.text === undefined ? 'NEWEST' : 'RELEVANCE';
}

/** The API accepts at most 20 values per filter (UC-02). */
const MAX_VALUES = 20;
export const CENTS_PER_PESO = 100;
/** Prices in the URL and in the panel are whole pesos. */
export const WHOLE_PESOS = /^\d{1,9}$/;

/*
 * The URL is what customers read and share, so its keys and values are Spanish, like the public paths (UC-02
 * storefront notes). The API keeps the English names.
 */
const KEYS = {
  sizes: 'talle',
  colors: 'color',
  materials: 'material',
  seasons: 'temporada',
  audiences: 'publico',
  minPrice: 'precio-desde',
  maxPrice: 'precio-hasta',
  sort: 'orden',
  text: 'q',
} as const;

const SEASON_SLUGS: Record<Season, string> = { SUMMER: 'verano', MID_SEASON: 'media-estacion', WINTER: 'invierno' };
const AUDIENCE_SLUGS: Record<Audience, string> = { WOMEN: 'mujer', MEN: 'hombre', KIDS: 'infantil' };
/** RELEVANCE is never written: it is only ever the default. NEWEST is written in a search, where it is not. */
const SORT_SLUGS: Record<Exclude<ProductSort, 'RELEVANCE'>, string> = {
  NEWEST: 'novedades',
  PRICE_ASC: 'menor-precio',
  PRICE_DESC: 'mayor-precio',
};

export const NO_FILTERS: ProductFilters = { sizes: [], colors: [], materials: [], seasons: [], audiences: [] };

function texts(params: URLSearchParams, key: string): string[] {
  return [...new Set(params.getAll(key).filter((value) => value !== ''))].slice(0, MAX_VALUES);
}

function fromSlugs<T extends string>(params: URLSearchParams, key: string, slugs: Record<T, string>): T[] {
  const bySlug = new Map(Object.entries<string>(slugs).map(([value, slug]) => [slug, value as T]));
  return [...new Set(params.getAll(key).flatMap((slug) => bySlug.get(slug) ?? []))];
}

function pesosToCents(value: string | null): number | undefined {
  return value !== null && WHOLE_PESOS.test(value) ? Number(value) * CENTS_PER_PESO : undefined;
}

function priceRange(params: URLSearchParams): Pick<ProductFilters, 'minPriceCents' | 'maxPriceCents'> {
  const min = pesosToCents(params.get(KEYS.minPrice));
  const max = pesosToCents(params.get(KEYS.maxPrice));
  if (min !== undefined && max !== undefined && min > max) {
    return {};
  }
  return { ...(min === undefined ? {} : { minPriceCents: min }), ...(max === undefined ? {} : { maxPriceCents: max }) };
}

/** The server reads only the first 100 characters of a search (UC-26). */
export const MAX_TEXT_LENGTH = 100;

/** A search text as the server reads it: trimmed and cut to its first 100 characters; absent when empty. */
export function searchText(raw: string | null): string | undefined {
  // Characters as the server counts them: code points.
  const text = Array.from((raw ?? '').trim()).slice(0, MAX_TEXT_LENGTH).join('').trim();
  return text === '' ? undefined : text;
}

function sortOf(params: URLSearchParams, text: string | undefined): ProductSort {
  const slug = params.get(KEYS.sort);
  const match = Object.entries(SORT_SLUGS).find(([, value]) => value === slug);
  return match === undefined ? defaultSort({ text }) : (match[0] as ProductSort);
}

/** Reads the text, filters and sort of a shared or typed URL, ignoring what is unknown or out of bounds. */
export function parseCriteria(params: URLSearchParams): ListingCriteria {
  const text = searchText(params.get(KEYS.text));
  return {
    filters: {
      sizes: texts(params, KEYS.sizes),
      colors: texts(params, KEYS.colors),
      materials: texts(params, KEYS.materials),
      seasons: fromSlugs(params, KEYS.seasons, SEASON_SLUGS),
      audiences: fromSlugs(params, KEYS.audiences, AUDIENCE_SLUGS),
      ...priceRange(params),
    },
    sort: sortOf(params, text),
    ...(text === undefined ? {} : { text }),
  };
}

/** The URL query of some criteria; the default sort and empty filters leave no trace. */
export function toSearchParams(criteria: ListingCriteria): URLSearchParams {
  const { filters, sort, text } = criteria;
  const params = new URLSearchParams();
  if (text !== undefined) {
    params.set(KEYS.text, text);
  }
  filters.sizes.forEach((size) => {
    params.append(KEYS.sizes, size);
  });
  filters.colors.forEach((color) => {
    params.append(KEYS.colors, color);
  });
  filters.materials.forEach((material) => {
    params.append(KEYS.materials, material);
  });
  filters.seasons.forEach((season) => {
    params.append(KEYS.seasons, SEASON_SLUGS[season]);
  });
  filters.audiences.forEach((audience) => {
    params.append(KEYS.audiences, AUDIENCE_SLUGS[audience]);
  });
  if (filters.minPriceCents !== undefined) {
    params.set(KEYS.minPrice, String(Math.floor(filters.minPriceCents / CENTS_PER_PESO)));
  }
  if (filters.maxPriceCents !== undefined) {
    params.set(KEYS.maxPrice, String(Math.floor(filters.maxPriceCents / CENTS_PER_PESO)));
  }
  if (sort !== defaultSort(criteria) && sort !== 'RELEVANCE') {
    params.set(KEYS.sort, SORT_SLUGS[sort]);
  }
  return params;
}

/** How many filters are applied: each chosen value counts, and the price range counts once. */
export function countFilters(filters: ProductFilters): number {
  const values =
    filters.sizes.length +
    filters.colors.length +
    filters.materials.length +
    filters.seasons.length +
    filters.audiences.length;
  const price = filters.minPriceCents !== undefined || filters.maxPriceCents !== undefined ? 1 : 0;
  return values + price;
}

/** The criteria of a listing that is not a search: a text in its URL is ignored, and so is a sort by relevance. */
export function withoutText({ filters, sort }: ListingCriteria): ListingCriteria {
  return { filters, sort: sort === 'RELEVANCE' ? 'NEWEST' : sort };
}
