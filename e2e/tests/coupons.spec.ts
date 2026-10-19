import { randomUUID } from 'node:crypto';
import type { Page } from '@playwright/test';
import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { expect, newBuyerContext, test } from '../support/fixtures.ts';
import { addToCart, confirmAndGoToPayment, fillCheckout, newBuyer, payAtMercadoPago } from '../support/storefront.ts';

/** Scenario 4 (UC-10): an unknown, an expired and a used-up coupon are refused and the total does not change. */

let product: ProductForSale;
const expiredCode = `VENCIDO-${randomUUID().slice(0, 6).toUpperCase()}`;
const usedUpCode = `UNAVEZ-${randomUUID().slice(0, 6).toUpperCase()}`;

/** A date in the store's time zone (America/Argentina/Buenos_Aires), days from today, as yyyy-mm-dd. */
function storeDate(daysFromToday: number): string {
  const day = new Date(Date.now() + daysFromToday * 24 * 60 * 60 * 1000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Buenos_Aires' }).format(day);
}

test.beforeAll(async ({ browser }) => {
  const admin = await AdminApi.open(await adminContext(browser));
  await admin.setShippingCost('SANTA_FE', 500_000);
  product = await admin.createProductForSale({
    name: 'Pijama Cupón',
    sizes: ['M'],
    colors: ['Azul'],
    priceCents: 3_000_000,
    stock: 10,
  });
  await admin.createCoupon({
    code: expiredCode,
    type: 'PERCENTAGE',
    discountValue: 10,
    validFrom: storeDate(-30),
    validUntil: storeDate(-1),
  });
  await admin.createCoupon({ code: usedUpCode, type: 'PERCENTAGE', discountValue: 10, maxUses: 1 });

  // The only use of the used-up coupon: a paid order (uses are counted when the order is paid, UC-15).
  const context = await newBuyerContext(browser);
  const page = await context.newPage();
  await addToCart(page, product, product.variants[0]!);
  await applyCoupon(page, usedUpCode);
  await expect(cart(page).getByText(`Descuento ${usedUpCode}`)).toBeVisible();
  await fillCheckout(page, newBuyer());
  await confirmAndGoToPayment(page);
  await payAtMercadoPago(page, 'approved');
  await expect(page.getByText('Pagado', { exact: true })).toBeVisible();
  await context.close();
});

function cart(page: Page) {
  return page.getByRole('dialog', { name: 'Mi carrito' });
}

async function applyCoupon(page: Page, code: string): Promise<void> {
  await cart(page).getByLabel('Código de cupón').fill(code);
  await cart(page).getByRole('button', { name: 'Aplicar' }).click();
}

test('unknown, expired and used-up coupons are refused without changing the total', async ({ page }) => {
  await addToCart(page, product, product.variants[0]!);
  const total = cart(page).getByRole('definition').last();
  await expect(total).toHaveText('$ 30.000');

  for (const [code, message] of [
    ['NOEXISTE', 'No encontramos ese cupón.'],
    [expiredCode, 'El cupón venció.'],
    [usedUpCode, 'Este cupón ya no tiene usos disponibles.'],
  ] as const) {
    await applyCoupon(page, code);
    await expect(cart(page).getByText(message)).toBeVisible();
    await expect(cart(page).getByText(/^Descuento/)).toHaveCount(0);
    await expect(total).toHaveText('$ 30.000');
  }
});
