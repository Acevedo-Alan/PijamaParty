import { randomUUID } from 'node:crypto';
import { expect, type Page } from '@playwright/test';
import type { ProductForSale, Variant } from './admin.ts';

/** The storefront steps a buyer takes, through the UI and its Spanish texts (frontend/src/i18n/es.ts). */

export interface Buyer {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  nationalId: string;
  street: string;
  number: string;
  city: string;
  province: string;
  postalCode: string;
}

/** A guest in Santa Fe with a unique email, so the emails each test counts are its own. */
export function newBuyer(): Buyer {
  return {
    firstName: 'María José',
    lastName: 'Pérez',
    email: `ana.${randomUUID().slice(0, 8)}@pijamaparty.test`,
    phone: '3424567890',
    nationalId: '30123456',
    street: 'San Martín',
    number: '1234',
    city: 'Santa Fe',
    province: 'Santa Fe',
    postalCode: '3000',
  };
}

/**
 * Picks an option of the product page as a shopper does, on its label: the radio itself is a dot under the swatch or
 * the size button (E7), which on a computer covers it.
 */
async function pickOption(page: Page, group: 'Talle' | 'Color', name: string): Promise<void> {
  const options = page.getByRole('group', { name: group });
  const radio = page.getByRole('radio', { name, exact: true });
  await options.locator('label').filter({ has: radio }).click();
  await expect(options.getByRole('radio', { name, exact: true })).toBeChecked();
}

/** UC-03, UC-08: picks the variant on the product page and adds it; the cart panel opens to confirm. */
export async function addToCart(page: Page, product: ProductForSale, variant: Variant): Promise<void> {
  await page.goto(`/producto/${product.slug}`);
  await pickOption(page, 'Talle', variant.size);
  await pickOption(page, 'Color', variant.color);
  await page.getByRole('button', { name: 'Agregar al carrito' }).click();
  await expect(page.getByRole('dialog', { name: 'Mi carrito' })).toBeVisible();
}

/** UC-13: from the open cart panel through the three steps, up to "Confirmar y pagar" (not clicked). */
export async function fillCheckout(page: Page, buyer: Buyer): Promise<void> {
  await page.getByRole('button', { name: 'Iniciar compra' }).click();
  await expect(page.getByRole('heading', { name: 'Finalizar compra' })).toBeVisible();
  // The cart panel slides out over the checkout and stays a modal dialog until it closes: until then the page behind
  // is inert and what is typed into it is lost (E7 motion, PanelDialog).
  await expect(page.getByRole('dialog', { name: 'Mi carrito' })).toBeHidden();

  await page.getByLabel('Nombre', { exact: true }).fill(buyer.firstName);
  await page.getByLabel('Apellido', { exact: true }).fill(buyer.lastName);
  await page.getByLabel('Email').fill(buyer.email);
  await page.getByLabel('Teléfono').fill(buyer.phone);
  await page.getByLabel('DNI').fill(buyer.nationalId);
  await page.getByLabel('Calle').fill(buyer.street);
  await page.getByLabel('Número').fill(buyer.number);
  await page.getByLabel('Ciudad').fill(buyer.city);
  await page.getByLabel('Provincia').selectOption({ label: buyer.province });
  await page.getByLabel('Código postal').fill(buyer.postalCode);
  // The step goes on only once the server quoted the shipping of the province; until then the button does nothing.
  await expect(async () => {
    await page.getByRole('button', { name: 'Continuar a pago' }).click();
    await expect(page.getByRole('heading', { name: 'Medio de pago' })).toBeVisible({ timeout: 1_000 });
  }).toPass();

  await page.getByRole('radio', { name: /MercadoPago/ }).check();
  await page.getByRole('button', { name: 'Revisar compra' }).click();

  await expect(page.getByRole('heading', { name: 'Revisá tu compra' })).toBeVisible();
  await page.getByRole('checkbox', { name: /Acepto los términos/ }).check();
}

/** Confirms the order and waits for the MercadoPago checkout (the stub's page). */
export async function confirmAndGoToPayment(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Confirmar y pagar' }).click();
  await expect(page.getByRole('heading', { name: 'MercadoPago (stub)' })).toBeVisible();
}

/** Pays at the MercadoPago stub. Approved, it sends the buyer back to the order's tracking page. */
export async function payAtMercadoPago(page: Page, outcome: 'approved' | 'rejected'): Promise<void> {
  await page.getByRole('button', { name: outcome === 'approved' ? 'Approve payment' : 'Reject payment' }).click();
  if (outcome === 'rejected') {
    await expect(page.getByRole('alert')).toHaveText('Payment rejected. Try another card.');
  }
}

/** The order number of the tracking page, "Pedido #1001". */
export async function trackedOrderNumber(page: Page): Promise<number> {
  const heading = page.getByRole('heading', { name: /^Pedido #\d+$/ });
  await expect(heading).toBeVisible();
  return Number((await heading.innerText()).replace('Pedido #', ''));
}

/** The whole purchase of one variant as a guest, paid and approved; returns the order number. */
export async function buyAsGuest(page: Page, product: ProductForSale, variant: Variant, buyer: Buyer): Promise<number> {
  await addToCart(page, product, variant);
  await fillCheckout(page, buyer);
  await confirmAndGoToPayment(page);
  await payAtMercadoPago(page, 'approved');
  await expect(page).toHaveURL(/\/pedido\//);
  await expect(page.getByText('Pagado', { exact: true })).toBeVisible();
  return trackedOrderNumber(page);
}
