import type { components } from '../../api/schema.d.ts';

type FilterOptionsResponse = components['schemas']['FilterOptionsResponse'];

export type Season = NonNullable<FilterOptionsResponse['seasons']>[number];
export type Audience = NonNullable<FilterOptionsResponse['audiences']>[number];
export type ProductSort = 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC';

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
} as const;

const SEASON_SLUGS: Record<Season, string> = { SUMMER: 'verano', MID_SEASON: 'media-estacion', WINTER: 'invierno' };
const AUDIENCE_SLUGS: Record<Audience, string> = { WOMEN: 'mujer', MEN: 'hombre', KIDS: 'infantil' };
const SORT_SLUGS: Record<Exclude<ProductSort, 'NEWEST'>, string> = {
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

function sortOf(params: URLSearchParams): ProductSort {
  const slug = params.get(KEYS.sort);
  const match = Object.entries(SORT_SLUGS).find(([, value]) => value === slug);
  return match === undefined ? 'NEWEST' : (match[0] as ProductSort);
}

/** Reads the filters and sort of a shared or typed URL, ignoring what is unknown or out of bounds. */
export function parseCriteria(params: URLSearchParams): ListingCriteria {
  return {
    filters: {
      sizes: texts(params, KEYS.sizes),
      colors: texts(params, KEYS.colors),
      materials: texts(params, KEYS.materials),
      seasons: fromSlugs(params, KEYS.seasons, SEASON_SLUGS),
      audiences: fromSlugs(params, KEYS.audiences, AUDIENCE_SLUGS),
      ...priceRange(params),
    },
    sort: sortOf(params),
  };
}

/** The URL query of some criteria; the default sort and empty filters leave no trace. */
export function toSearchParams({ filters, sort }: ListingCriteria): URLSearchParams {
  const params = new URLSearchParams();
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
  if (sort !== 'NEWEST') {
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
