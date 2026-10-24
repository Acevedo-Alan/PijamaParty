import { expect, test } from '@playwright/test';
import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';

/**
 * T-E6-23 (RNF-31.1, RNF-31.2): against the production Caddy image (lighthouse/caddy.setup.ts), robots.txt keeps
 * search engines out of the private paths and points to the API's sitemap; the pages carry noindex, their description
 * and canonical URL, and the product page its structured data.
 */

let product: ProductForSale;

test.beforeAll(async ({ browser }) => {
  const context = await adminContext(browser);
  const admin = await AdminApi.open(context);
  product = await admin.createProductForSale({
    name: 'Pijama Indexado',
    sizes: ['M', 'L'],
    colors: ['Rosa'],
    priceCents: 1_850_000,
    stock: 10,
  });
  await context.close();
});

test('robots.txt disallows the private paths and points to the sitemap', async ({ request }) => {
  const response = await request.get('/robots.txt');

  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('text/plain');
  const robots = await response.text();
  for (const path of ['/admin', '/cuenta', '/checkout', '/pedido', '/buscar', '/api/']) {
    expect(robots).toContain(`Disallow: ${path}\n`);
  }
  expect(robots).toMatch(/^Sitemap: https:\/\/[^/]+\/sitemap\.xml$/m);
});

test('the sitemap lists the home page, the categories and the listed products', async ({ request }) => {
  const response = await request.get('/sitemap.xml');

  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('application/xml');
  const sitemap = await response.text();
  expect(sitemap).toContain('<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">');
  expect(sitemap).toMatch(/<loc>[^<]+\/<\/loc>/);
  expect(sitemap).toContain(`/categoria/${product.category.slug}</loc>`);
  expect(sitemap).toContain(`/producto/${product.slug}</loc>`);
});

test('the product page carries its description, canonical URL and structured data', async ({ page }) => {
  await page.goto(`/producto/${product.slug}?color=Rosa`);
  await expect(page.getByRole('heading', { level: 1, name: product.name })).toBeVisible();

  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', /\/producto\/[a-z0-9-]+$/);
  await expect(page.locator('meta[name="description"]')).toHaveAttribute('content', /^\$\s?18\.500\. \S/);
  await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
  const data = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '') as {
    '@type': string;
    name: string;
    offers: { sku: string; priceCurrency: string; availability: string }[];
  };
  expect(data['@type']).toBe('Product');
  expect(data.name).toBe(product.name);
  expect(data.offers.map((offer) => offer.sku).sort()).toEqual(product.variants.map((variant) => variant.sku).sort());
  expect(data.offers.every((offer) => offer.priceCurrency === 'ARS')).toBe(true);
  expect(data.offers.every((offer) => offer.availability === 'https://schema.org/InStock')).toBe(true);
});

for (const path of ['/cuenta/ingresar', '/buscar?q=pijama', '/no-existe']) {
  test(`${path} is kept out of search engines`, async ({ page }) => {
    await page.goto(path);

    await expect(page.locator('meta[name="robots"][content="noindex"]').first()).toBeAttached();
  });
}
