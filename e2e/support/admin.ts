import { randomUUID } from 'node:crypto';
import type { Browser, BrowserContext, Page } from '@playwright/test';
import { prepareContext } from './fixtures.ts';

/** Where admin.setup.ts leaves the administrator's session and authenticator secret (git-ignored). */
export const ADMIN_STORAGE_STATE = '.auth/admin.json';
export const ADMIN_TOTP_SECRET = '.auth/admin-totp-secret.txt';

export type Province = 'SANTA_FE' | 'CORDOBA' | 'BUENOS_AIRES';

export interface Variant {
  id: string;
  sku: string;
  size: string;
  color: string;
}

export interface Category {
  id: string;
  slug: string;
}

export interface ProductForSale {
  id: string;
  name: string;
  slug: string;
  category: Category;
  variants: Variant[];
}

interface ApiAnswer<T> {
  status: number;
  body: T;
}

/** A browser signed in as the administrator, with the session admin.setup.ts saved. */
export async function adminContext(browser: Browser): Promise<BrowserContext> {
  const context = await browser.newContext({ storageState: ADMIN_STORAGE_STATE });
  await prepareContext(context);
  return context;
}

/**
 * The back office API, called from a page of the store so the browser sends the session and CSRF cookies as the
 * back office does (RNF-29.1). For the data a test needs before its real steps; the flows under test use the UI.
 */
export class AdminApi {
  private readonly page: Page;

  private constructor(page: Page) {
    this.page = page;
  }

  static async open(context: BrowserContext): Promise<AdminApi> {
    const page = await context.newPage();
    await page.goto('/');
    const api = new AdminApi(page);
    // Also leaves the CSRF cookie that the writes need.
    await api.call('GET', '/api/admin/auth/me', undefined, 200);
    return api;
  }

  async call<T>(method: string, path: string, body: unknown, expected: number, headers: Record<string, string> = {}): Promise<T> {
    const answer = await this.page.evaluate(
      async ({ method, path, body, headers }): Promise<ApiAnswer<unknown>> => {
        const csrf = document.cookie
          .split(';')
          .map((cookie) => cookie.trim())
          .find((cookie) => cookie.startsWith('XSRF-TOKEN='));
        const response = await fetch(path, {
          method,
          headers: {
            ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
            ...(csrf === undefined ? {} : { 'X-XSRF-TOKEN': decodeURIComponent(csrf.slice('XSRF-TOKEN='.length)) }),
            ...headers,
          },
          ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        });
        const text = await response.text();
        return { status: response.status, body: text === '' ? null : (JSON.parse(text) as unknown) };
      },
      { method, path, body, headers },
    );
    if (answer.status !== expected) {
      throw new Error(`${method} ${path} answered ${String(answer.status)}: ${JSON.stringify(answer.body)}`);
    }
    return answer.body as T;
  }

  /** UC-12: the shipping cost of one province, in cents. */
  async setShippingCost(province: Province, costCents: number): Promise<void> {
    await this.call('PUT', '/api/admin/shipping/zones', { zones: [{ province, costCents }] }, 200);
  }

  /** UC-12: the order total from which shipping is free, in cents; null removes it. */
  async setFreeShippingThreshold(thresholdCents: number | null): Promise<void> {
    await this.call('PUT', '/api/admin/shipping/free-shipping', { thresholdCents }, 200);
  }

  /**
   * UC-05, UC-06, UC-07: a published product in a new category (or the one given), every size and color combination
   * priced and with stock. The names end in a unique suffix, so tests never collide.
   */
  async createProductForSale(options: {
    name: string;
    sizes: string[];
    colors: string[];
    priceCents: number;
    stock: number;
    category?: Category;
  }): Promise<ProductForSale> {
    const suffix = randomUUID().slice(0, 8);
    const category =
      options.category ??
      (await this.call<Category>('POST', '/api/admin/catalog/categories', { name: `Categoría ${suffix}` }, 201));
    const created = await this.call<{ id: string }>(
      'POST',
      '/api/admin/catalog/products',
      {
        categoryId: category.id,
        name: `${options.name} ${suffix}`,
        description: 'Producto de las pruebas de extremo a extremo.',
        material: 'Algodón',
        season: 'WINTER',
        audience: 'WOMEN',
      },
      201,
    );
    const product = await this.call<{ name: string; slug: string; variants: Variant[] }>(
      'POST',
      `/api/admin/catalog/products/${created.id}/variants/generate`,
      { sizes: options.sizes, colors: options.colors },
      200,
    );
    for (const variant of product.variants) {
      await this.call('PATCH', `/api/admin/catalog/variants/${variant.id}`, { priceCents: options.priceCents }, 200);
      await this.adjustStock(variant.id, options.stock);
    }
    await this.call('POST', `/api/admin/catalog/products/${created.id}/publish`, undefined, 200);
    return { id: created.id, name: product.name, slug: product.slug, category, variants: product.variants };
  }

  /** UC-06: uploads a photo (the server converts it with libvips); it goes after the product's other photos. */
  async uploadPhoto(productId: string, png: Buffer): Promise<void> {
    await this.upload(`/api/admin/catalog/products/${productId}/images`, png);
  }

  /** UC-32: gives the home page's character banner its texts and photo, and publishes it. */
  async publishHomeBanner(png: Buffer): Promise<void> {
    await this.call(
      'PUT',
      '/api/admin/home/banner',
      {
        title: 'Kigurumis',
        body: 'Tus personajes favoritos, para dormir abrigado.',
        linkText: 'Ver kigurumis',
        linkPath: '/',
      },
      200,
    );
    await this.upload('/api/admin/home/banner/photo', png);
    await this.call('POST', '/api/admin/home/banner/publish', undefined, 200);
  }

  /** A photo sent as the back office sends it: multipart, with an Idempotency-Key. */
  private async upload(path: string, png: Buffer): Promise<void> {
    const status = await this.page.evaluate(
      async ({ path, base64, key }) => {
        const csrf = document.cookie
          .split(';')
          .map((cookie) => cookie.trim())
          .find((cookie) => cookie.startsWith('XSRF-TOKEN='));
        const bytes = Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
        const form = new FormData();
        form.append('file', new Blob([bytes], { type: 'image/png' }), 'photo.png');
        const response = await fetch(path, {
          method: 'POST',
          headers: {
            'Idempotency-Key': key,
            ...(csrf === undefined ? {} : { 'X-XSRF-TOKEN': decodeURIComponent(csrf.slice('XSRF-TOKEN='.length)) }),
          },
          body: form,
        });
        return response.status;
      },
      { path, base64: png.toString('base64'), key: randomUUID() },
    );
    if (status !== 201) {
      throw new Error(`Uploading a photo answered ${String(status)}: is libvips installed (VIPS_BIN_DIR)?`);
    }
  }

  /** UC-07: adds (or with a negative quantity, removes) units. */
  async adjustStock(variantId: string, quantity: number): Promise<void> {
    await this.call('POST', '/api/admin/stock-adjustments', { variantId, quantity }, 201, {
      'Idempotency-Key': randomUUID(),
    });
  }

  /** UC-11: a code coupon. */
  async createCoupon(coupon: {
    code: string;
    type: 'PERCENTAGE' | 'FIXED_AMOUNT';
    discountValue: number;
    validFrom?: string;
    validUntil?: string;
    maxUses?: number;
  }): Promise<void> {
    await this.call('POST', '/api/admin/coupons', coupon, 201);
  }

  /** The units of a variant the store can still sell (UC-03): the storefront's own answer. */
  async availableUnits(slug: string, variantId: string): Promise<number> {
    const detail = await this.call<{ variants: { id: string; available: number }[] }>(
      'GET',
      `/api/catalog/products/${slug}`,
      undefined,
      200,
    );
    const variant = detail.variants.find((candidate) => candidate.id === variantId);
    if (variant === undefined) {
      throw new Error(`Variant ${variantId} is not on the product page`);
    }
    return variant.available;
  }

  async close(): Promise<void> {
    await this.page.close();
  }
}
