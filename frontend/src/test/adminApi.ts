import { vi } from 'vitest';
import { api } from '../api/client.ts';
import type { components } from '../api/schema.d.ts';
import { answer } from './catalogApi.ts';

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
/**
 * A reply (an openapi-fetch result, or an Error to reject with), a function of the request options returning one,
 * or a list answered in order whose last one repeats.
 */
type Route = unknown;

export interface RequestOptions {
  params?: { path?: Record<string, string>; query?: Record<string, unknown>; header?: Record<string, string> };
  body?: unknown;
}

export interface Call {
  method: Method;
  path: string;
  options: RequestOptions;
}

const METHODS: readonly Method[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];

/**
 * Serves the typed client by "METHOD /path" (the path template, as the code calls it). A call without a route fails
 * the request, so a test sees what it forgot to stub.
 */
export function stubApi(routes: Record<string, Route>) {
  const calls: Call[] = [];
  const queues = new Map<string, unknown[]>();
  for (const method of METHODS) {
    vi.spyOn(api, method).mockImplementation(((path: string, options: RequestOptions = {}) => {
      calls.push({ method, path, options });
      const name = `${method} ${path}`;
      const route = routes[name];
      if (route === undefined) {
        return Promise.reject(new Error(`No stub for ${name}`));
      }
      if (Array.isArray(route)) {
        const queue = queues.get(name) ?? [...(route as unknown[])];
        queues.set(name, queue);
        const next: unknown = queue.length > 1 ? queue.shift() : queue[0];
        return next instanceof Error ? Promise.reject(next) : Promise.resolve(next);
      }
      const reply: unknown = typeof route === 'function' ? (route as (o: RequestOptions) => unknown)(options) : route;
      return reply instanceof Error ? Promise.reject(reply) : Promise.resolve(reply);
    }) as never);
  }
  return {
    calls,
    /** The calls to one "METHOD /path", in order. */
    callsTo: (name: string) => calls.filter((call) => `${call.method} ${call.path}` === name),
  };
}

/** A problem+json answer with its extra properties. */
export function problem(status: number, properties: Record<string, unknown> = {}) {
  return answer(status, { status, title: 'Problem', ...properties });
}

/** A request that never reached the server (lost connection). */
export function connectionLost() {
  return new TypeError('Failed to fetch');
}

export const signedIn = answer(200, { id: 'admin-1', email: 'owner@example.com', name: 'Dueña' });
export const signedOut = problem(401);

type CategoryBody = components['schemas']['CategoryResponse'];
type ProductBody = components['schemas']['ProductResponse'];
type VariantBody = components['schemas']['VariantResponse'];
type ImageBody = components['schemas']['ImageResponse'];

export function category(id: string, name: string, active = true): CategoryBody {
  return { id, name, slug: name.toLowerCase(), position: 0, active };
}

export function variant(size: string, color: string, priceCents: number | null = null, active = true): VariantBody {
  return { id: `v-${size}-${color}`, sku: `PP-${size}${color}`, size, color, priceCents, active };
}

export function photo(id: string, color: string | null = null): ImageBody {
  return {
    id,
    color,
    placeholder: 'data:image/webp;base64,AAAA',
    files: [
      { width: 400, url: `/uploads/${id}-400.webp` },
      { width: 800, url: `/uploads/${id}-800.webp` },
    ],
  };
}

/** A hidden product of category "c-1" without variants or photos, unless overridden. */
export function adminProduct(overrides: Partial<ProductBody> = {}): ProductBody {
  return {
    id: 'p-1',
    categoryId: 'c-1',
    name: 'Pijama Luna',
    slug: 'pijama-luna',
    description: 'Pijama largo.',
    material: 'Algodón',
    season: 'WINTER',
    audience: 'WOMEN',
    published: false,
    sizes: [],
    colors: [],
    variants: [],
    images: [],
    ...overrides,
  };
}

type CouponBody = components['schemas']['AdminCouponResponse'];

/** An active 10 % coupon without limits nor uses, unless overridden. */
export function coupon(id: string, code: string, overrides: Partial<CouponBody> = {}): CouponBody {
  return {
    id,
    code,
    type: 'PERCENTAGE',
    discountValue: 10,
    validFrom: null,
    validUntil: null,
    maxUses: null,
    minimumPurchaseCents: null,
    active: true,
    uses: 0,
    ...overrides,
  };
}
