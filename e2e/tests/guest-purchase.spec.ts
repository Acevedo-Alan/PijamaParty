import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { emailsTo, paymentsOf } from '../support/backdoors.ts';
import { expect, test } from '../support/fixtures.ts';
import { addToCart, confirmAndGoToPayment, fillCheckout, newBuyer, payAtMercadoPago, trackedOrderNumber } from '../support/storefront.ts';

/** Scenario 1 (UC-13 to UC-17): a guest buys with an approved card, end to end. */

let product: ProductForSale;

test.beforeAll(async ({ browser }) => {
  const context = await adminContext(browser);
  const admin = await AdminApi.open(context);
  await admin.setShippingCost('SANTA_FE', 500_000);
  product = await admin.createProductForSale({
    name: 'Pijama Luna',
    sizes: ['M'],
    colors: ['Rosa'],
    priceCents: 2_000_000,
    stock: 5,
  });
  await context.close();
});

test('a guest buys with an approved card and gets one confirmation email', async ({ page }) => {
  const variant = product.variants[0]!;
  const buyer = newBuyer();

  await page.goto('/');
  await page.getByRole('link', { name: product.name }).first().click();
  await expect(page.getByRole('heading', { name: product.name })).toBeVisible();
  await addToCart(page, product, variant);
  await fillCheckout(page, buyer);
  // $ 20.000 plus $ 5.000 of shipping to Santa Fe, priced by the server.
  await expect(page.getByText('$ 25.000').first()).toBeVisible();
  await confirmAndGoToPayment(page);
  await expect(page.getByLabel('Amount')).toHaveText('25000.00');

  await payAtMercadoPago(page, 'approved');

  await expect(page).toHaveURL(/\/pedido\//);
  await expect(page.getByText('Pagado', { exact: true })).toBeVisible();
  const orderNumber = await trackedOrderNumber(page);
  const payments = await paymentsOf(orderNumber);
  expect(payments.map((payment) => payment.status)).toEqual(['approved']);
  await expect.poll(async () => (await emailsTo(buyer.email)).length, { timeout: 30_000 }).toBe(1);
});
