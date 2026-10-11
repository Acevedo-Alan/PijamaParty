import { vi, type MockInstance } from 'vitest';
import { api } from '../api/client.ts';
import type { components } from '../api/schema.d.ts';

export type ListItem = components['schemas']['ProductListItemResponse'];
export type FilterOptionsBody = components['schemas']['FilterOptionsResponse'];

/** What openapi-fetch resolves to: data on success, the problem otherwise, and always the response. */
export function answer(status: number, body?: unknown) {
  return {
    data: status < 300 ? body : undefined,
    error: status < 300 ? undefined : body,
    response: new Response(null, { status }),
  } as never;
}

export function product(slug: string, overrides: Partial<ListItem> = {}): ListItem {
  return {
    slug,
    name: `Pijama ${slug}`,
    image: {
      placeholder: 'data:image/webp;base64,AAAA',
      files: [
        { width: 400, url: `/media/${slug}-400.webp` },
        { width: 800, url: `/media/${slug}-800.webp` },
        { width: 1200, url: `/media/${slug}-1200.webp` },
      ],
    },
    fromPriceCents: 1_234_500,
    soldOut: false,
    ...overrides,
  };
}

export function products(count: number, prefix: string): ListItem[] {
  return Array.from({ length: count }, (_, index) => product(`${prefix}-${String(index)}`));
}

export function listed(items: ListItem[], hasMore = false) {
  return answer(200, { items, hasMore });
}

export function filterOptions(overrides: Partial<FilterOptionsBody> = {}) {
  return answer(200, {
    sizes: ['S', 'M', 'L'],
    colors: ['Rosa', 'Celeste'],
    materials: ['Algodón', 'Satén'],
    seasons: ['SUMMER', 'WINTER'],
    audiences: ['WOMEN', 'KIDS'],
    minPriceCents: 1_200_000,
    maxPriceCents: 3_450_050,
    ...overrides,
  });
}

interface CatalogAnswers {
  /** Answers of GET /api/catalog/products in call order; the last one repeats. */
  listings: unknown[];
  /** Answer of GET /api/catalog/filters. */
  filters?: unknown;
}

/** Serves the catalog GET calls by path, so the listing and the filter options can load in any order. */
export function stubCatalogApi({ listings, filters = filterOptions() }: CatalogAnswers): MockInstance {
  const queue = [...listings];
  return vi.spyOn(api, 'GET').mockImplementation(((path: string) => {
    if (path === '/api/catalog/filters') {
      return Promise.resolve(filters);
    }
    const next = queue.length > 1 ? queue.shift() : queue[0];
    return Promise.resolve(next);
  }) as never);
}

/** The query sent with each call to the given path, in call order. */
export function queriesTo(get: MockInstance, path: string): unknown[] {
  return get.mock.calls
    .filter((call) => call[0] === path)
    .map((call) => (call[1] as { params: { query: unknown } }).params.query);
}

export type DetailBody = components['schemas']['ProductDetailResponse'];
type DetailVariantBody = components['schemas']['VariantDetailResponse'];
type DetailImageBody = components['schemas']['DetailImageResponse'];

export function detailVariant(size: string, color: string, available: number, priceCents = 2_500_000): DetailVariantBody {
  return { id: `${size}-${color}`, size, color, priceCents, available };
}

export function detailImage(name: string, color: string | null): DetailImageBody {
  return {
    color,
    placeholder: 'data:image/webp;base64,AAAA',
    files: [
      { width: 400, url: `/media/${name}-400.webp` },
      { width: 800, url: `/media/${name}-800.webp` },
      { width: 1200, url: `/media/${name}-1200.webp` },
    ],
  };
}

/** A listed product with pink M in stock and light-blue M out of stock (the example of UC-03). */
export function productDetail(overrides: Partial<DetailBody> = {}): DetailBody {
  return {
    slug: 'luna',
    name: 'Pijama Luna',
    description: 'Pijama largo de algodón.',
    material: 'Algodón',
    season: 'WINTER',
    audience: 'WOMEN',
    fromPriceCents: 2_500_000,
    sizes: ['M'],
    colors: ['Rosa', 'Celeste'],
    variants: [detailVariant('M', 'Rosa', 3), detailVariant('M', 'Celeste', 0)],
    images: [detailImage('frente', null), detailImage('rosa', 'Rosa'), detailImage('celeste', 'Celeste')],
    ...overrides,
  };
}
