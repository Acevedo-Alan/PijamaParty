import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { expect, newBuyerContext, test } from '../support/fixtures.ts';
import { addToCart, fillCheckout, newBuyer } from '../support/storefront.ts';

/** Scenario 3 (UC-13, UC-16): two buyers confirm the last unit at the same time; exactly one order is placed. */

let admin: AdminApi;
let product: ProductForSale;

test.beforeAll(async ({ browser }) => {
  admin = await AdminApi.open(await adminContext(browser));
  await admin.setShippingCost('SANTA_FE', 500_000);
  product = await admin.createProductForSale({
    name: 'Pijama Última',
    sizes: ['L'],
    colors: ['Verde'],
    priceCents: 1_800_000,
    stock: 1,
  });
});

test('only one of two buyers gets the last unit', async ({ browser }) => {
  const variant = product.variants[0]!;
  const buyers = await Promise.all([newBuyerContext(browser), newBuyerContext(browser)]);
  const pages = await Promise.all(buyers.map((context) => context.newPage()));
  for (const page of pages) {
    await addToCart(page, product, variant);
    await fillCheckout(page, newBuyer());
  }

  await Promise.all(pages.map((page) => page.getByRole('button', { name: 'Confirmar y pagar' }).click()));

  const atPayment = (index: number) => pages[index]!.getByRole('heading', { name: 'MercadoPago (stub)' });
  const refused = (index: number) =>
    pages[index]!.getByText('Algunos productos cambiaron. Revisá tu carrito antes de confirmar.');
  await expect(atPayment(0).or(refused(0))).toBeVisible();
  await expect(atPayment(1).or(refused(1))).toBeVisible();
  const paid = await Promise.all([atPayment(0).isVisible(), atPayment(1).isVisible()]);
  expect(paid.filter(Boolean)).toHaveLength(1);
  expect(await admin.availableUnits(product.slug, variant.id)).toBe(0);

  await Promise.all(buyers.map((context) => context.close()));
});
