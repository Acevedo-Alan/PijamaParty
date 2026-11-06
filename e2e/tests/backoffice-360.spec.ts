import { randomUUID } from 'node:crypto';
import type { Page, TestInfo } from '@playwright/test';
import { AdminApi, adminContext } from '../support/admin.ts';
import { expect, test } from '../support/fixtures.ts';
import { buyAsGuest, newBuyer, registerAndSignIn } from '../support/storefront.ts';

/**
 * T-E8-14 (E8a, "Terminado cuando": every screen works from 360 px): each new back-office screen at 360 × 800. None
 * may scroll sideways, and every link, button and field is a 44 px touch target (RNF-26). A full-page screenshot of
 * each is attached to the report and left in test-results/ to look at.
 */

test.use({ viewport: { width: 360, height: 800 } });

interface Offender {
  text: string;
  width: number;
  height: number;
}

/**
 * Visible links, buttons and fields under 44 px tall (or 24 px wide), outside screen-reader-only text. With a modal
 * dialog open (the menu panel), only the dialog counts: the page behind it cannot be tapped.
 */
async function smallTargets(page: Page): Promise<Offender[]> {
  return page.evaluate(() => {
    const root: ParentNode = document.querySelector('dialog[open]') ?? document;
    const targets = root.querySelectorAll<HTMLElement>(
      'a[href], button, input:not([type="hidden"]), select, textarea, [role="button"], [role="tab"]',
    );
    return [...targets]
      .filter((element) => {
        const box = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        const hidden = box.width === 0 || box.height === 0 || style.visibility === 'hidden';
        // The skip link waits off screen until focused. A control inside its label (a checkbox, a radio, a switch) is
        // tapped through the label, so the label is its target.
        const label = element.closest('label');
        const labelBox = label?.getBoundingClientRect();
        const targetBox = labelBox !== undefined && labelBox.height >= box.height ? labelBox : box;
        const exempt =
          element.closest('.sr-only') !== null ||
          element.classList.contains('admin-skip-link') ||
          (element instanceof HTMLInputElement && (element.type === 'checkbox' || element.type === 'radio'));
        // Rounded, as the layout lands on fractions of a pixel (a 44 px link can measure 43.99).
        return !hidden && !exempt && (Math.round(targetBox.height) < 44 || Math.round(targetBox.width) < 24);
      })
      .map((element) => {
        const box = element.getBoundingClientRect();
        const text = (element.innerText || element.getAttribute('aria-label') || element.outerHTML.slice(0, 80)).trim();
        return { text, width: Math.round(box.width), height: Math.round(box.height) };
      });
  });
}

async function pageScrollsSideways(page: Page): Promise<boolean> {
  return page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
}

/**
 * Checks one screen once its heading shows and its data has loaded (a block that arrives later, such as the summary's
 * period selector, would otherwise go unchecked); a print page is checked for width only.
 */
async function walk(page: Page, info: TestInfo, name: string, options: { touchTargets: boolean } = { touchTargets: true }) {
  await page.waitForLoadState('networkidle');
  await expect(page.getByText('Cargando…', { exact: true })).toHaveCount(0);
  expect(await pageScrollsSideways(page), `${name} scrolls sideways at 360 px`).toBe(false);
  if (options.touchTargets) {
    expect(await smallTargets(page), `${name}: touch targets under 44 px`).toEqual([]);
  }
  const screenshot = await page.screenshot({ path: info.outputPath(`${name}-360.png`), fullPage: true });
  await info.attach(`${name} at 360 px`, { body: screenshot, contentType: 'image/png' });
}

test('every new back-office screen works at 360 px', async ({ browser, page }, info) => {
  const context = await adminContext(browser);
  await context.addInitScript(() => {
    window.print = () => undefined;
  });
  const api = await AdminApi.open(context);
  await api.setShippingCost('SANTA_FE', 500_000);
  const product = await api.createProductForSale({
    name: 'Pijama Luna',
    sizes: ['M'],
    colors: ['Rosa'],
    priceCents: 2_000_000,
    stock: 3,
  });

  // A paid order (its detail and packing slip) and a registered customer (its profile).
  const buyer = newBuyer();
  await registerAndSignIn(page, buyer, `clave-${randomUUID().slice(0, 8)}`);
  const orderNumber = await buyAsGuest(page, product, product.variants[0]!, buyer);
  const orders = await api.call<{ items: { id: string; number: number }[] }>(
    'GET',
    `/api/admin/orders?q=${String(orderNumber)}`,
    undefined,
    200,
  );
  const orderId = orders.items.find((order) => order.number === orderNumber)!.id;
  const customers = await api.call<{ items: { id: string }[] }>(
    'GET',
    `/api/admin/customers?q=${encodeURIComponent(buyer.email)}`,
    undefined,
    200,
  );
  const customerId = customers.items[0]!.id;

  const admin = await context.newPage();
  const screens: { name: string; path: string; heading: string | RegExp }[] = [
    { name: 'resumen', path: '/admin', heading: 'Resumen' },
    { name: 'pedidos', path: `/admin/pedidos?buscar=${String(orderNumber)}`, heading: 'Pedidos' },
    { name: 'pedido', path: `/admin/pedidos/${orderId}`, heading: `Pedido #${String(orderNumber)}` },
    { name: 'clientes', path: '/admin/clientes', heading: 'Clientes' },
    { name: 'ficha-cliente', path: `/admin/clientes/${customerId}`, heading: `${buyer.firstName} ${buyer.lastName}` },
    { name: 'stock', path: '/admin/stock', heading: 'Stock' },
    { name: 'datos-de-la-tienda', path: '/admin/datos-de-la-tienda', heading: 'Datos de la tienda' },
    { name: 'actividad', path: '/admin/actividad', heading: 'Actividad' },
  ];
  for (const screen of screens) {
    await admin.goto(screen.path);
    await expect(admin.getByRole('heading', { level: 1, name: screen.heading })).toBeVisible();
    await walk(admin, info, screen.name);
  }

  // The packing slip is a print page: its width matters, its links are not for tapping in a hurry.
  await admin.goto(`/admin/pedidos/${orderId}/remito`);
  await expect(admin.getByRole('article', { name: `Pedido #${String(orderNumber)}` })).toBeVisible();
  await walk(admin, info, 'remito', { touchTargets: false });

  // The menu panel, opened from its button (T-E8-05).
  await admin.goto('/admin');
  await admin.getByRole('button', { name: 'Menú' }).click();
  await expect(admin.getByRole('dialog').getByRole('navigation', { name: 'Secciones del backoffice' })).toBeVisible();
  await walk(admin, info, 'menu');

  await context.close();
});
