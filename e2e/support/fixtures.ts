import { test as base, type Browser, type BrowserContext, type BrowserContextOptions } from '@playwright/test';
import { MERCADOPAGO_SDK_URL, mercadoPagoSdk } from './mercadoPagoSdk.ts';

/**
 * Every browser context of the tests: MercadoPago's SDK is the fake card form of support/mercadoPagoSdk.ts (the real
 * one loads from the internet, T-E3-31), and the cookie banner is already answered with "Rechazar", so no Google or
 * Meta script loads (RNF-25) and the banner covers nothing.
 */
export async function prepareContext(context: BrowserContext): Promise<void> {
  await context.route(MERCADOPAGO_SDK_URL, async (route) => {
    await route.fulfill({ contentType: 'text/javascript', body: mercadoPagoSdk });
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
