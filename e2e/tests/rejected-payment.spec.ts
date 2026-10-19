import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { advanceClock, emailsTo, paymentsOf, resetClock } from '../support/backdoors.ts';
import { expect, test } from '../support/fixtures.ts';
import { addToCart, confirmAndGoToPayment, fillCheckout, newBuyer, payAtMercadoPago, trackedOrderNumber } from '../support/storefront.ts';

/**
 * Scenario 2 (UC-15, UC-16; T-E3-07): a rejected payment keeps the reservation so the buyer can pay again; once the
 * 30-minute reservation is over, the order expires and the stock is released.
 */

let admin: AdminApi;
let product: ProductForSale;

test.beforeAll(async ({ browser }) => {
  admin = await AdminApi.open(await adminContext(browser));
  await admin.setShippingCost('SANTA_FE', 500_000);
  product = await admin.createProductForSale({
    name: 'Camisón Estrella',
    sizes: ['S'],
    colors: ['Lila'],
    priceCents: 1_500_000,
    stock: 1,
  });
});

test.afterEach(async () => {
  await resetClock();
});

test('a rejected payment keeps the stock reserved until the reservation expires', async ({ page }) => {
  const variant = product.variants[0]!;
  const buyer = newBuyer();
  await addToCart(page, product, variant);
  await fillCheckout(page, buyer);
  await confirmAndGoToPayment(page);

  await payAtMercadoPago(page, 'rejected');

  // The buyer can try another card on the same checkout.
  await expect(page.getByRole('button', { name: 'Approve payment' })).toBeVisible();
  // Back in the store, the order awaits payment and still holds the only unit.
  await page.goto('/checkout/result');
  await expect(page).toHaveURL(/\/pedido\//);
  await expect(page.getByText('Pago pendiente', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Pagar' })).toBeVisible();
  const orderNumber = await trackedOrderNumber(page);
  expect((await paymentsOf(orderNumber)).map((payment) => payment.status)).toEqual(['rejected']);
  expect(await admin.availableUnits(product.slug, variant.id)).toBe(0);

  await advanceClock(31);

  await expect.poll(() => admin.availableUnits(product.slug, variant.id), { timeout: 30_000 }).toBe(1);
  await page.reload();
  await expect(page.getByText('Expirado', { exact: true })).toBeVisible();
  expect(await emailsTo(buyer.email)).toEqual([]);
});
