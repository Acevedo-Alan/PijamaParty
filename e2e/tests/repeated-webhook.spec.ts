import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { emailsTo, notifyAgain, paymentsOf } from '../support/backdoors.ts';
import { expect, test } from '../support/fixtures.ts';
import { buyAsGuest, newBuyer } from '../support/storefront.ts';

/**
 * Scenario 5 (UC-15, UC-16, UC-17; RNF-20): MercadoPago delivers the same notification again; the order is paid
 * once, the stock is deducted once and the buyer gets one confirmation email.
 */

let admin: AdminApi;
let product: ProductForSale;

test.beforeAll(async ({ browser }) => {
  admin = await AdminApi.open(await adminContext(browser));
  await admin.setShippingCost('SANTA_FE', 500_000);
  product = await admin.createProductForSale({
    name: 'Pijama Eco',
    sizes: ['XL'],
    colors: ['Gris'],
    priceCents: 2_200_000,
    stock: 3,
  });
});

test('a repeated webhook does not pay, deduct or email twice', async ({ page }) => {
  const variant = product.variants[0]!;
  const buyer = newBuyer();
  const orderNumber = await buyAsGuest(page, product, variant, buyer);
  await expect.poll(async () => (await emailsTo(buyer.email)).length, { timeout: 30_000 }).toBe(1);
  expect(await admin.availableUnits(product.slug, variant.id)).toBe(2);
  const [payment] = await paymentsOf(orderNumber);

  expect(await notifyAgain(payment!.id)).toBe(200);
  expect(await notifyAgain(payment!.id)).toBe(200);

  // The outbox runs every 2 seconds in the e2e profile: wait several runs for an email that must not come.
  await page.waitForTimeout(6_000);
  expect(await emailsTo(buyer.email)).toHaveLength(1);
  expect(await admin.availableUnits(product.slug, variant.id)).toBe(2);
  await page.reload();
  await expect(page.getByText('Pagado', { exact: true })).toBeVisible();
});
