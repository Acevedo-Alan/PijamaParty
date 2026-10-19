import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { emailsTo } from '../support/backdoors.ts';
import { MERCADOPAGO_STUB_PORT } from '../stubs/settings.ts';
import { runK6 } from './k6.ts';

/**
 * T-E6-14 (RNF-16): the scenarios of the spec's load test, one k6 script each (load/k6). This file seeds the data
 * through the back office and, after each script, checks what the database kept: k6 sees the answers, not the stock
 * or the emails. The scenarios run in order: the checkouts' orders are the ones the webhooks pay.
 */

test.describe.configure({ mode: 'serial' });

/** Tells this run's orders apart from earlier runs' on a reused backend. */
const RUN = randomUUID().slice(0, 8);
const CHECKOUTS = 10;
const STOCK = 100;

interface OrderSummary {
  number: number;
  status: string;
  buyerName: string;
}

let admin: AdminApi;
let forCheckout: ProductForSale;
let lastUnit: ProductForSale;

/** This run's orders of the checkout scenario with a status, from the back office's order list (UC-18). */
async function checkoutOrders(status: string): Promise<OrderSummary[]> {
  const ofThisRun = new RegExp(`^Carga ${RUN} \\d+$`);
  const found: OrderSummary[] = [];
  for (let page = 0; ; page++) {
    const listed = await admin.call<{ items: OrderSummary[]; hasMore: boolean }>(
      'GET', `/api/admin/orders?status=${status}&page=${String(page)}`, undefined, 200,
    );
    found.push(...listed.items.filter((order) => ofThisRun.test(order.buyerName)));
    if (!listed.hasMore) {
      return found;
    }
  }
}

/** UC-07: the units on hand of a variant, reserved ones included. */
async function onHand(variantId: string): Promise<number> {
  const movements = await admin.call<{ onHand: number }>(
    'GET', `/api/admin/variants/${variantId}/stock-movements`, undefined, 200,
  );
  return movements.onHand;
}

test.beforeAll(async ({ browser }) => {
  admin = await AdminApi.open(await adminContext(browser));
  await admin.setShippingCost('SANTA_FE', 500_000);
  // A catalog to browse: three categories of six products, two sizes each.
  for (const name of ['Pijamas', 'Camisones', 'Batas']) {
    let category: ProductForSale['category'] | undefined;
    for (let n = 1; n <= 6; n++) {
      const product = await admin.createProductForSale({
        name: `${name} carga ${String(n)}`,
        sizes: ['M', 'L'],
        colors: ['Azul'],
        priceCents: 1_500_000 + n * 100_000,
        stock: 50,
        ...(category === undefined ? {} : { category }),
      });
      category = product.category;
    }
  }
  forCheckout = await admin.createProductForSale({
    name: 'Pijama carga checkout', sizes: ['M'], colors: ['Rosa'], priceCents: 2_000_000, stock: STOCK,
  });
  lastUnit = await admin.createProductForSale({
    name: 'Pijama carga última', sizes: ['S'], colors: ['Lila'], priceCents: 1_900_000, stock: 1,
  });
});

test('50 shoppers browse at once with the p95 of the catalog under 400 ms', async () => {
  await runK6('browse.ts', { DURATION: process.env.LOAD_DURATION ?? '2m' });
});

test('10 simultaneous checkouts, each sent twice with one key, place 10 orders and reserve 10 units', async () => {
  const variant = forCheckout.variants[0]!;
  await runK6('checkout.ts', { VARIANT_ID: variant.id, RUN, BUYERS: String(CHECKOUTS) });

  const orders = await checkoutOrders('AWAITING_PAYMENT');
  expect(new Set(orders.map((order) => order.buyerName)).size).toBe(CHECKOUTS);
  expect(orders).toHaveLength(CHECKOUTS);
  expect(await admin.availableUnits(forCheckout.slug, variant.id)).toBe(STOCK - CHECKOUTS);
  expect(await onHand(variant.id)).toBe(STOCK);
});

test('the same webhook delivered 5 times at once pays each order once, deducts once and emails once', async ({ page }) => {
  const variant = forCheckout.variants[0]!;
  const orders = await checkoutOrders('AWAITING_PAYMENT');
  expect(orders).toHaveLength(CHECKOUTS);
  const notifications = [];
  for (const order of orders) {
    const answer = await fetch(`http://127.0.0.1:${String(MERCADOPAGO_STUB_PORT)}/__e2e/payments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ externalReference: String(order.number) }),
    });
    expect(answer.status).toBe(201);
    notifications.push(await answer.json());
  }

  await runK6('webhooks.ts', { NOTIFICATIONS: JSON.stringify(notifications), COPIES: '5' });

  await expect.poll(async () => (await checkoutOrders('PAID')).length).toBe(CHECKOUTS);
  expect(await checkoutOrders('AWAITING_PAYMENT')).toHaveLength(0);
  expect(await admin.availableUnits(forCheckout.slug, variant.id)).toBe(STOCK - CHECKOUTS);
  expect(await onHand(variant.id)).toBe(STOCK - CHECKOUTS);
  const buyers = Array.from({ length: CHECKOUTS }, (_, n) => `carga.${RUN}.${String(n + 1)}@pijamaparty.test`);
  for (const buyer of buyers) {
    await expect.poll(async () => (await emailsTo(buyer)).length).toBe(1);
  }
  // The outbox runs every 2 seconds in the e2e profile: wait several runs for an email that must not come.
  await page.waitForTimeout(6_000);
  for (const buyer of buyers) {
    expect(await emailsTo(buyer)).toHaveLength(1);
  }
});

test('20 buyers at once for the last unit: exactly one order', async () => {
  const variant = lastUnit.variants[0]!;
  await runK6('last-unit.ts', { VARIANT_ID: variant.id, RUN });

  expect(await admin.availableUnits(lastUnit.slug, variant.id)).toBe(0);
  expect(await onHand(variant.id)).toBe(1);
});

test('under extreme saturation the API answers 200 or 503 and then serves as usual', async () => {
  await runK6('saturation.ts', { DURATION: process.env.LOAD_SATURATION_DURATION ?? '30s' });
});

test('over each per-minute limit, through Caddy, the answer is 429 with Retry-After', async () => {
  await runK6('rate-limits.ts', { VARIANT_ID: forCheckout.variants[0]!.id });
});
