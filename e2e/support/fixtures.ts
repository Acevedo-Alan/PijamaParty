import { test as base, type Browser, type BrowserContext, type BrowserContextOptions } from '@playwright/test';
import { MERCADOPAGO_CHECKOUT_ORIGIN, MERCADOPAGO_STUB_PORT } from '../stubs/settings.ts';

/**
 * Every browser context of the tests: the MercadoPago checkout host goes to the stub, and the cookie banner is
 * already answered with "Rechazar", so no Google or Meta script loads (RNF-25) and the banner covers nothing.
 */
export async function prepareContext(context: BrowserContext): Promise<void> {
  await context.route(`${MERCADOPAGO_CHECKOUT_ORIGIN}/**`, async (route) => {
    const url = new URL(route.request().url());
    const response = await route.fetch({
      url: `http://127.0.0.1:${String(MERCADOPAGO_STUB_PORT)}${url.pathname}${url.search}`,
      maxRedirects: 0,
    });
    await route.fulfill({ response });
  });
  await context.addInitScript(() => {
    localStorage.setItem('pp-consent', JSON.stringify({ choice: 'rejected', decidedAt: Date.now() }));
  });
}

/** Another buyer, in a browser of its own (scenario 3: two buyers at once), with the project's options. */
export async function newBuyerContext(browser: Browser, options: BrowserContextOptions = {}): Promise<BrowserContext> {
  const context = await browser.newContext(options);
  await prepareContext(context);
  return context;
}

export const test = base.extend({
  context: async ({ context }, use) => {
    await prepareContext(context);
    await use(context);
  },
});

export { expect } from '@playwright/test';
