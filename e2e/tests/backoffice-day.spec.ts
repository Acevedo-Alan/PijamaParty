import { randomUUID } from 'node:crypto';
import { AdminApi, adminContext } from '../support/admin.ts';
import { expect, test } from '../support/fixtures.ts';
import { buyAsGuest, newBuyer, registerAndSignIn } from '../support/storefront.ts';

/**
 * T-E8-14 (E8a, "Terminado cuando"): a working day in the back office, after a registered customer bought in the
 * store. The summary counts the order; it is found by its number, moved to preparation and its packing slip opened;
 * the customer is found by email and its profile shows the purchase; stock is adjusted from the Stock screen; the
 * store's opening hours change without a deploy; and "Actividad" lists each change, with no personal data.
 */

test('a day in the back office: summary, order, packing slip, customer, stock, store data and activity', async ({
  browser,
  page,
}) => {
  const suffix = randomUUID().slice(0, 8);
  const context = await adminContext(browser);
  // The packing slip opens the print dialog on load (T-E8-07); the test has no printer to wait for.
  await context.addInitScript(() => {
    window.print = () => undefined;
  });
  const api = await AdminApi.open(context);
  await api.setShippingCost('SANTA_FE', 500_000);
  const product = await api.createProductForSale({
    name: 'Pijama Estrella',
    sizes: ['M'],
    colors: ['Rosa'],
    priceCents: 2_000_000,
    stock: 4,
  });
  const variant = product.variants[0]!;

  // The customer's own browser (the page fixture): an account, then a purchase linked to it (UC-21).
  const buyer = newBuyer();
  const fullName = `${buyer.firstName} ${buyer.lastName}`;
  await registerAndSignIn(page, buyer, `clave-${suffix}`);
  const orderNumber = await buyAsGuest(page, product, variant, buyer);
  const orderLink = `#${String(orderNumber)}`;

  const admin = await context.newPage();

  // UC-33: the summary opens first and lists the new order.
  await admin.goto('/admin');
  await expect(admin.getByRole('heading', { level: 1, name: 'Resumen' })).toBeVisible();
  await expect(
    admin.getByRole('region', { name: 'Pedidos recientes' }).getByRole('link', { name: orderLink, exact: true }),
  ).toBeVisible();

  // UC-18: found by its number, moved to preparation, and its packing slip (T-E8-06, T-E8-07).
  await admin.goto('/admin/pedidos');
  const orderSearch = admin.getByRole('search', { name: 'Buscar pedidos' });
  await orderSearch.getByLabel('Buscar').fill(String(orderNumber));
  await orderSearch.getByRole('button', { name: 'Buscar' }).click();
  await admin.getByRole('link', { name: orderLink, exact: true }).click();
  await expect(admin.getByRole('heading', { level: 1, name: `Pedido ${orderLink}` })).toBeVisible();
  await admin.getByRole('button', { name: 'Preparar' }).click();
  await expect(admin.getByText('Pedido actualizado: En preparación.')).toBeVisible();
  await admin.getByRole('link', { name: 'Imprimir remito' }).click();
  // The slip itself is an article named after the order; "Remito del pedido" is the tab's title.
  await expect(admin.getByRole('article', { name: `Pedido ${orderLink}` })).toBeVisible();
  await expect(admin.getByText(variant.sku)).toBeVisible();
  await admin.getByRole('link', { name: 'Volver al pedido' }).click();
  await expect(admin.getByRole('heading', { level: 1, name: `Pedido ${orderLink}` })).toBeVisible();

  // UC-34: found by email; the profile counts the purchase and its order returns to it (T-E8-10, T-E8-11).
  await admin.goto('/admin/clientes');
  const customerSearch = admin.getByRole('search', { name: 'Buscar clientes' });
  await customerSearch.getByLabel('Buscar').fill(buyer.email);
  await customerSearch.getByRole('button', { name: 'Buscar' }).click();
  await admin.getByRole('link', { name: fullName }).click();
  await expect(admin.getByRole('heading', { level: 1, name: fullName })).toBeVisible();
  const figures = admin.getByRole('region', { name: 'Compras del cliente' });
  await expect(figures.getByText('de 1 pedido')).toBeVisible();
  await admin.getByRole('region', { name: 'Pedidos' }).getByRole('link', { name: orderLink, exact: true }).click();
  await admin.getByRole('link', { name: 'Volver al cliente' }).click();
  await expect(admin.getByRole('heading', { level: 1, name: fullName })).toBeVisible();

  // UC-07: two units back from the Stock screen, with a note that names the buyer (T-E8-09).
  await admin.goto('/admin/stock');
  const stockSearch = admin.getByRole('search', { name: 'Buscar en el stock' });
  await stockSearch.getByLabel('Buscar').fill(variant.sku);
  await stockSearch.getByRole('button', { name: 'Buscar' }).click();
  const adjust = admin.getByRole('form', { name: `Ajustar ${product.name}, M · Rosa` });
  await adjust.getByLabel('Cantidad').fill('2');
  const note = `Devolución de ${buyer.firstName}`;
  await adjust.getByLabel('Motivo (opcional)').fill(note);
  await adjust.getByRole('button', { name: '+ Sumar' }).click();
  await expect(adjust.getByText(/^Stock actualizado: /)).toBeVisible();

  // UC-36: the opening hours, without a deploy.
  await admin.goto('/admin/datos-de-la-tienda');
  await admin.getByLabel('Horario de atención').fill(`Lunes a viernes de 9 a 18 h (${suffix})`);
  await admin.getByRole('button', { name: 'Guardar' }).click();
  await expect(admin.getByText('Cambios guardados.')).toBeVisible();

  // UC-37: each change is in "Actividad", newest first.
  await admin.goto('/admin/actividad');
  const entries = admin.getByRole('listitem');
  await expect(entries.filter({ hasText: 'Datos de la tienda cambiados' }).first()).toBeVisible();
  await expect(entries.filter({ hasText: 'Stock ajustado' }).filter({ hasText: variant.sku }).first()).toBeVisible();
  await expect(entries.filter({ hasText: 'Ficha de cliente consultada' }).first()).toBeVisible();
  await expect(
    entries.filter({ hasText: 'Estado del pedido cambiado' }).filter({ hasText: orderLink }).first(),
  ).toBeVisible();

  // ...and none of them carries the buyer's data or the stock note (UC-37, R-05).
  const stored = JSON.stringify(await api.call('GET', '/api/admin/activity', undefined, 200));
  for (const personal of [fullName, buyer.email, buyer.phone, buyer.nationalId, note]) {
    expect(stored).not.toContain(personal);
  }
  await context.close();
});
