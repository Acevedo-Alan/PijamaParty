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

/** The cart of a visitor without one, which the header of every public page reads (UC-08). */
export const NO_CART = answer(200, { items: [], subtotalCents: 0, discountCents: 0, totalCents: 0 });

/** Nobody signed in: what the header of every public page reads from GET /api/account/me (UC-19). */
export const SIGNED_OUT = answer(401);

/**
 * A GET stub that also answers the header's question of who is signed in (signed out, unless told otherwise), so
 * the stub's own answers are left for the page.
 */
export function withHeader(get: (path: string, init?: never) => Promise<unknown>, account: unknown = SIGNED_OUT): never {
  return ((path: string, init?: never) =>
    path === '/api/account/me' ? Promise.resolve(account) : get(path, init)) as never;
}

interface CatalogAnswers {
  /** Answers of GET /api/catalog/products in call order; the last one repeats. */
  listings: unknown[];
  /** Answer of GET /api/catalog/filters. */
  filters?: unknown;
  /** Answer of GET /api/catalog/suggestions, or a function of the text. */
  suggestions?: unknown;
  /** Answer of GET /api/catalog/categories. */
  categories?: unknown;
  /** Answer of GET /api/cart. */
  cart?: unknown;
  /** Answer of GET /api/home/banner: none published unless told otherwise. */
  banner?: unknown;
  /** Answer of GET /api/shipping/rates, which the header's free-shipping strip reads (UC-31): none unless told. */
  shippingRates?: unknown;
  /** Answer of GET /api/checkout/payment-providers, which the footer reads (UC-31): MercadoPago only unless told. */
  paymentProviders?: unknown;
}

/** Serves the catalog GET calls by path, so the listing and the filter options can load in any order. */
export function stubCatalogApi({
  listings,
  filters = filterOptions(),
  suggestions = suggested([]),
  categories = shopCategories([]),
  cart = NO_CART,
  banner = answer(204),
  shippingRates = NO_SHIPPING_RATES,
  paymentProviders = MERCADOPAGO_ONLY,
}: CatalogAnswers): MockInstance {
  const queue = [...listings];
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string, init?: { params?: { query?: { q?: string } } }) => {
    if (path === '/api/catalog/filters') {
      return Promise.resolve(filters);
    }
    if (path === '/api/catalog/suggestions') {
      return Promise.resolve(
        typeof suggestions === 'function' ? (suggestions as (q: string) => unknown)(init?.params?.query?.q ?? '') : suggestions,
      );
    }
    if (path === '/api/catalog/categories') {
      return Promise.resolve(categories);
    }
    if (path === '/api/cart') {
      return Promise.resolve(cart);
    }
    if (path === '/api/home/banner') {
      return Promise.resolve(banner);
    }
    if (path === SHIPPING_RATES_PATH) {
      return Promise.resolve(shippingRates);
    }
    if (path === PAYMENT_PROVIDERS_PATH) {
      return Promise.resolve(paymentProviders);
    }
    const next = queue.length > 1 ? queue.shift() : queue[0];
    return Promise.resolve(next);
  }));
}

export function suggested(items: ListItem[]) {
  return answer(200, { items: items.map(({ slug, name, image }) => ({ slug, name, image })) });
}

export function shopCategories(items: { slug: string; name: string }[]) {
  return answer(200, { items });
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
  return { id: `${size}-${color}`, sku: `PP-${size}${color.toUpperCase()}`, size, color, priceCents, available };
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
    id: '7d1c2e4a-5b6f-4c3d-8e9f-0a1b2c3d4e5f',
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

/** GET of the approved reviews below the product page (UC-24, T-E5-07). */
export const REVIEWS_PATH = '/api/catalog/products/{slug}/reviews';

/** A product without approved reviews. */
export const NO_REVIEWS = answer(200, { average: null, count: 0, reviews: [] });

/** GETs of the purchase summary below the add to cart (RNF-31.6, T-E5-12). */
export const SHIPPING_RATES_PATH = '/api/shipping/rates';
export const PAYMENT_PROVIDERS_PATH = '/api/checkout/payment-providers';

/** No province with shipping yet, and no free shipping. */
export const NO_SHIPPING_RATES = answer(200, { zones: [], freeShippingThresholdCents: null });

/** MODO switched off. */
export const MERCADOPAGO_ONLY = answer(200, { providers: ['MERCADOPAGO'] });

/**
 * The reads of the header and the footer besides the account (UC-08, UC-31): an empty cart, no free shipping and
 * MercadoPago only; undefined otherwise.
 */
export function headerRead(path: string): unknown {
  if (path === '/api/cart') {
    return NO_CART;
  }
  if (path === PAYMENT_PROVIDERS_PATH) {
    return MERCADOPAGO_ONLY;
  }
  return path === SHIPPING_RATES_PATH ? NO_SHIPPING_RATES : undefined;
}

/** The answer of a purchase-summary read, or undefined for any other path. */
export function purchaseInfo(path: string): unknown {
  if (path === SHIPPING_RATES_PATH) {
    return NO_SHIPPING_RATES;
  }
  return path === PAYMENT_PROVIDERS_PATH ? MERCADOPAGO_ONLY : undefined;
}

/** The product page's own reads: no approved reviews below it, the purchase summary, and {@code detail}. */
export function detailOrNoReviews(path: string, detail: unknown = answer(200, productDetail())): unknown {
  return path === REVIEWS_PATH ? NO_REVIEWS : (purchaseInfo(path) ?? detail);
}
