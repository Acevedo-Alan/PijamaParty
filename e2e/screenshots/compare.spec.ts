import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { devices, test, type Page } from '@playwright/test';
import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { photoPng } from '../support/photo.ts';
import { addToCart } from '../support/storefront.ts';

/**
 * T-E7-11: home, listing, product page, cart and checkout of the store and of the prototype, on a phone and on a
 * computer, written to screenshots/ for a person to compare (playwright.screenshots.config.ts). Both are shot with
 * reduced motion, so no carousel moves and every entrance has finished.
 */

const OUTPUT = 'screenshots';
const PROTOTYPE = pathToFileURL(join(import.meta.dirname, '..', '..', 'docs', 'design', 'prototype', 'index.html')).href;
/** Enough for two rows of the listing and "Completá tu look". */
const PRODUCTS = 6;
const FREE_SHIPPING_CENTS = 5_000_000;

// The browser comes from the project, not from a describe group.
const { defaultBrowserType: _phoneBrowser, ...phone } = devices['Pixel 7'];
const VIEWPORTS = {
  phone,
  desktop: { viewport: { width: 1440, height: 900 } },
} as const;
const SCREENS = ['home', 'listing', 'product', 'cart', 'checkout'] as const;

type Screen = (typeof SCREENS)[number];

test.describe.configure({ mode: 'serial' });

let products: ProductForSale[];

test.beforeAll(async ({ browser }) => {
  const context = await adminContext(browser);
  const admin = await AdminApi.open(context);
  await admin.setShippingCost('SANTA_FE', 500_000);
  // The prototype's free shipping from $50.000: the header strip and the cart's bar show only with a threshold.
  await admin.setFreeShippingThreshold(FREE_SHIPPING_CENTS);
  const options = { sizes: ['S', 'M', 'L'], colors: ['Rosa', 'Celeste'], priceCents: 2_450_000, stock: 10 };
  const first = await admin.createProductForSale({ name: 'Pijama Satén', ...options });
  products = [first];
  for (const name of ['Camisón Algodón', 'Pijama Polar', 'Conjunto Modal', 'Short Pijama', 'Pijama Térmico']) {
    products.push(await admin.createProductForSale({ name, ...options, category: first.category }));
  }
  for (const [index, product] of products.entries()) {
    await admin.uploadPhoto(product.id, photoPng(index + 1));
  }
  await admin.publishHomeBanner(photoPng(PRODUCTS + 1, 1500, 1200));
  await context.close();
});

test.afterAll(async ({ browser }) => {
  // The backend may be reused by the end-to-end tests, whose totals expect no free shipping.
  const context = await adminContext(browser);
  await (await AdminApi.open(context)).setFreeShippingThreshold(null);
  await context.close();

  const rows = Object.keys(VIEWPORTS).flatMap((viewport) =>
    SCREENS.map(
      (screen) => `<h2>${viewport}: ${screen}</h2><div class="pair">`
        + `<figure><figcaption>Tienda</figcaption><img src="${viewport}/${screen}-store.png" alt=""></figure>`
        + `<figure><figcaption>Prototipo</figcaption><img src="${viewport}/${screen}-prototype.png" alt=""></figure>`
        + '</div>',
    ),
  );
  writeFileSync(
    join(OUTPUT, 'index.html'),
    '<!doctype html><meta charset="utf-8"><title>Tienda y prototipo</title>'
      + '<style>body{font-family:sans-serif;margin:16px}.pair{display:flex;gap:16px;align-items:flex-start}'
      + 'figure{margin:0;flex:1}img{width:100%;border:1px solid #ccc}</style>'
      + rows.join(''),
  );
});

/**
 * A full page scrolls through so every lazy photo loads, then back to the top once the network is quiet; an open
 * panel only waits for its photos.
 */
async function settle(page: Page, fullPage: boolean): Promise<void> {
  await page.evaluate(async (scroll) => {
    if (!scroll) {
      return;
    }
    for (let y = 0; y < document.documentElement.scrollHeight; y += window.innerHeight / 2) {
      window.scrollTo({ top: y, behavior: 'instant' });
      await new Promise((resolve) => setTimeout(resolve, 150));
    }
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, fullPage);
  await page.waitForLoadState('networkidle');
  await page.evaluate(() => document.fonts.ready);
  // The sticky bars and the notices settle after the scroll.
  await page.waitForTimeout(500);
}

/** Adds the prototype's first product in stock, through its own script (its functions are globals). */
async function fillPrototypeCart(page: Page): Promise<void> {
  await page.evaluate(`(() => {
    const product = allProducts[0];
    const variant = product.variantes.find((v) => v.stock > 0);
    addToCart(product, variant.color, variant.talle);
  })()`);
}

const STORE: Record<Screen, (page: Page) => Promise<void>> = {
  home: (page) => page.goto('/').then(() => undefined),
  listing: (page) => page.goto(`/categoria/${products[0]!.category.slug}`).then(() => undefined),
  product: (page) => page.goto(`/producto/${products[0]!.slug}`).then(() => undefined),
  cart: (page) => addToCart(page, products[0]!, products[0]!.variants[0]!),
  checkout: async (page) => {
    await addToCart(page, products[0]!, products[0]!.variants[0]!);
    await page.goto('/checkout');
  },
};

const PROTOTYPE_SCREENS: Record<Screen, (page: Page) => Promise<void>> = {
  home: (page) => page.goto(PROTOTYPE).then(() => undefined),
  listing: (page) => page.goto(PROTOTYPE).then(() => undefined),
  product: (page) => page.goto(`${PROTOTYPE}#producto/p1`).then(() => undefined),
  cart: async (page) => {
    await page.goto(`${PROTOTYPE}#producto/p1`);
    await fillPrototypeCart(page);
    await page.evaluate('openDrawer()');
  },
  checkout: async (page) => {
    await page.goto(PROTOTYPE);
    await fillPrototypeCart(page);
    await page.evaluate("goTo('checkout')");
  },
};

for (const [viewport, device] of Object.entries(VIEWPORTS)) {
  test.describe(viewport, () => {
    test.use({ ...device, reducedMotion: 'reduce' });

    for (const screen of SCREENS) {
      test(screen, async ({ page, context }) => {
        mkdirSync(join(OUTPUT, viewport), { recursive: true });
        // The panels cover the page: only what the shopper sees.
        const fullPage = screen !== 'cart';

        await STORE[screen](page);
        await settle(page, fullPage);
        await page.screenshot({ path: join(OUTPUT, viewport, `${screen}-store.png`), fullPage });

        const prototype = await context.newPage();
        await PROTOTYPE_SCREENS[screen](prototype);
        await settle(prototype, fullPage);
        const path = join(OUTPUT, viewport, `${screen}-prototype.png`);
        if (screen === 'listing') {
          // The prototype's listing is the grid section of its home page.
          await prototype.locator('#catalog-anchor').screenshot({ path });
        } else {
          await prototype.screenshot({ path, fullPage });
        }
      });
    }
  });
}
