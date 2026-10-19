import { mkdirSync, writeFileSync } from 'node:fs';
import { chromium, expect, test, type BrowserContext } from '@playwright/test';
import lighthouse from 'lighthouse';
import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { photoPng } from '../support/photo.ts';
import { addToCart } from '../support/storefront.ts';
import { CADDY_URL } from '../stubs/settings.ts';

/**
 * T-E6-02 (RNF-11, RNF-31.4): Lighthouse in mobile mode (its default slow 4G and mid-range phone) on the pages
 * shoppers land on and the checkout. Each one must paint its largest element in under 3 seconds and score 90 or
 * more in accessibility. The pages are measured cold: the browser cache is cleared first, the cookies are kept.
 */

const LCP_BUDGET_MS = 3_000;
const MIN_ACCESSIBILITY = 0.9;
const DEBUGGING_PORT = 9333;
const REPORTS = 'lighthouse-report';
/** Enough for the first rows of a listing on a phone and on a computer. */
const PRODUCTS = 6;

test.describe.configure({ mode: 'serial' });

let products: ProductForSale[];
/** One browser for every page, which Lighthouse drives through its debugging port. */
let browser: BrowserContext;

test.beforeAll(async ({ browser: adminBrowser }) => {
  const context = await adminContext(adminBrowser);
  const admin = await AdminApi.open(context);
  await admin.setShippingCost('SANTA_FE', 500_000);
  const first = await admin.createProductForSale({
    name: 'Pijama Lighthouse',
    sizes: ['M'],
    colors: ['Rosa'],
    priceCents: 2_000_000,
    stock: 10,
  });
  products = [first];
  while (products.length < PRODUCTS) {
    products.push(
      await admin.createProductForSale({
        name: 'Pijama Lighthouse',
        sizes: ['M'],
        colors: ['Rosa'],
        priceCents: 2_000_000,
        stock: 10,
        category: first.category,
      }),
    );
  }
  for (const [index, product] of products.entries()) {
    await admin.uploadPhoto(product.id, photoPng(index + 1));
  }
  await context.close();

  mkdirSync(REPORTS, { recursive: true });
  browser = await chromium.launchPersistentContext('', {
    baseURL: CADDY_URL,
    ignoreHTTPSErrors: true,
    args: [`--remote-debugging-port=${String(DEBUGGING_PORT)}`, '--ignore-certificate-errors'],
  });
});

test.afterAll(async () => {
  await browser.close();
});

interface Measure {
  largestContentfulPaintMs: number;
  accessibility: number;
}

/** Runs Lighthouse on one page, cold, and keeps its HTML report. */
async function measure(path: string, report: string): Promise<Measure> {
  const page = browser.pages()[0] ?? (await browser.newPage());
  const session = await browser.newCDPSession(page);
  await session.send('Network.clearBrowserCache');
  await session.detach();

  const result = await lighthouse(
    `${CADDY_URL}${path}`,
    {
      port: DEBUGGING_PORT,
      output: 'html',
      logLevel: 'error',
      onlyCategories: ['performance', 'accessibility'],
      // Keeps the cart's cookie for the checkout; the cache was cleared above.
      disableStorageReset: true,
    },
  );
  if (result === undefined) {
    throw new Error(`Lighthouse returned nothing for ${path}`);
  }
  writeFileSync(`${REPORTS}/${report}.html`, result.report as string);
  const { lhr } = result;
  if (lhr.runtimeError !== undefined) {
    throw new Error(`Lighthouse could not measure ${path}: ${lhr.runtimeError.message}`);
  }
  const measured = {
    largestContentfulPaintMs: lhr.audits['largest-contentful-paint']?.numericValue ?? Number.POSITIVE_INFINITY,
    accessibility: lhr.categories['accessibility']?.score ?? 0,
  };
  console.log(`${report}: LCP ${String(Math.round(measured.largestContentfulPaintMs))} ms, `
    + `accessibility ${String(Math.round(measured.accessibility * 100))}, `
    + `performance ${String(Math.round((lhr.categories['performance']?.score ?? 0) * 100))}`);
  return measured;
}

function expectWithinBudget(measured: Measure): void {
  expect(measured.largestContentfulPaintMs).toBeLessThan(LCP_BUDGET_MS);
  expect(measured.accessibility).toBeGreaterThanOrEqual(MIN_ACCESSIBILITY);
}

test('photos are kept a year and index.html is checked on every visit', async () => {
  const detail = await browser.request.get(`/api/catalog/products/${products[0]!.slug}`);
  const { images } = (await detail.json()) as { images: { files: { url: string }[] }[] };
  const photo = await browser.request.get(images[0]!.files[0]!.url);
  expect(photo.status()).toBe(200);
  expect(photo.headers()['cache-control']).toBe('public, max-age=31536000, immutable');

  const missing = await browser.request.get('/uploads/no-such-photo-400.webp');
  expect(missing.status()).toBe(404);
  expect(missing.headers()['cache-control']).toBeUndefined();

  for (const path of ['/', `/producto/${products[0]!.slug}`]) {
    const html = await browser.request.get(path);
    expect(html.headers()['cache-control']).toBe('no-cache');
  }
});

test('the home page paints in under 3 seconds on a phone with 4G', async () => {
  expectWithinBudget(await measure('/', 'home'));
});

test('a category listing paints in under 3 seconds on a phone with 4G', async () => {
  expectWithinBudget(await measure(`/categoria/${products[0]!.category.slug}`, 'listing'));
});

test('a product page paints in under 3 seconds on a phone with 4G', async () => {
  expectWithinBudget(await measure(`/producto/${products[0]!.slug}`, 'product'));
});

test('the checkout with one product paints in under 3 seconds on a phone with 4G', async () => {
  const page = browser.pages()[0] ?? (await browser.newPage());
  await addToCart(page, products[0]!, products[0]!.variants[0]!);

  expectWithinBudget(await measure('/checkout', 'checkout'));
});
