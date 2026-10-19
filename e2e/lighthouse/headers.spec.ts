import { expect, test, type APIResponse, type Page } from '@playwright/test';
import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { photoPng } from '../support/photo.ts';
import { addToCart } from '../support/storefront.ts';

/**
 * T-E6-03 (RNF-15, RNF-29.9): the security headers of the production Caddy image (lighthouse/caddy.setup.ts) on the
 * storefront, the API and a photo, and no content security policy violation on the pages shoppers use. The image is
 * built without analytics ids, so the analytics domains are not exercised here (T-E6-16).
 */

let product: ProductForSale;

test.beforeAll(async ({ browser }) => {
  const context = await adminContext(browser);
  const admin = await AdminApi.open(context);
  await admin.setShippingCost('SANTA_FE', 500_000);
  product = await admin.createProductForSale({
    name: 'Pijama Cabeceras',
    sizes: ['M'],
    colors: ['Rosa'],
    priceCents: 2_000_000,
    stock: 10,
  });
  await admin.uploadPhoto(product.id, photoPng(1));
  await context.close();
});

function expectSecurityHeaders(response: APIResponse): void {
  const headers = response.headers();
  expect(headers['strict-transport-security']).toBe('max-age=31536000; includeSubDomains');
  expect(headers['content-security-policy']).toContain("default-src 'self'");
  expect(headers['content-security-policy']).toContain("frame-ancestors 'none'");
  expect(headers['content-security-policy']).toContain("object-src 'none'");
  expect(headers['x-content-type-options']).toBe('nosniff');
  expect(headers['referrer-policy']).toBe('strict-origin-when-cross-origin');
  expect(headers['permissions-policy']).toBe('camera=(), microphone=(), geolocation=()');
  expect(headers['server']).toBeUndefined();
}

test('the storefront, the API and the photos carry the security headers', async ({ request }) => {
  const detail = await request.get(`/api/catalog/products/${product.slug}`);
  const { images } = (await detail.json()) as { images: { files: { url: string }[] }[] };

  for (const path of ['/', `/producto/${product.slug}`, '/api/health', images[0]!.files[0]!.url]) {
    const response = await request.get(path);
    expect(response.status(), path).toBe(200);
    expectSecurityHeaders(response);
  }
  // An answer of the backend that is not a success keeps them too.
  const missing = await request.get('/api/catalog/products/no-such-product');
  expect(missing.status()).toBe(404);
  expectSecurityHeaders(missing);
});

/** Collects the CSP violations the page reports, from before its first script runs. */
async function watchViolations(page: Page): Promise<() => Promise<string[]>> {
  await page.addInitScript(() => {
    const seen: string[] = [];
    (window as unknown as { cspViolations: string[] }).cspViolations = seen;
    document.addEventListener('securitypolicyviolation', (event) => {
      seen.push(`${event.effectiveDirective} ${event.blockedURI}`);
    });
  });
  return () => page.evaluate(() => (window as unknown as { cspViolations: string[] }).cspViolations);
}

test('the home page, a listing, a product page and the checkout break no content security policy rule', async ({
  page,
}) => {
  const violations = await watchViolations(page);
  const visited: Record<string, string[]> = {};

  for (const path of ['/', `/categoria/${product.category.slug}`, `/producto/${product.slug}`]) {
    await page.goto(path, { waitUntil: 'networkidle' });
    visited[path] = await violations();
  }
  await addToCart(page, product, product.variants[0]!);
  await page.goto('/checkout', { waitUntil: 'networkidle' });
  visited['/checkout'] = await violations();

  expect(visited).toEqual({
    '/': [],
    [`/categoria/${product.category.slug}`]: [],
    [`/producto/${product.slug}`]: [],
    '/checkout': [],
  });
});
