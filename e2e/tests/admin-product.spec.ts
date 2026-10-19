import { randomUUID } from 'node:crypto';
import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { expect, test } from '../support/fixtures.ts';
import { buyAsGuest, newBuyer } from '../support/storefront.ts';

/**
 * Scenario 6 (UC-05, UC-06, UC-07, then UC-13 to UC-15): the administrator creates a product with two variants and
 * their stock in the back office, and a guest, in another browser, buys one of them.
 */

test('a product created in the back office can be bought', async ({ browser, page }) => {
  const suffix = randomUUID().slice(0, 8);
  const categoryName = `Pijamas ${suffix}`;
  const productName = `Pijama Nube ${suffix}`;

  const context = await adminContext(browser);
  const admin = await context.newPage();
  await (await AdminApi.open(context)).setShippingCost('SANTA_FE', 500_000);

  await admin.goto('/admin/categorias');
  await admin.getByLabel('Nueva categoría').fill(categoryName);
  await admin.getByRole('button', { name: 'Crear' }).click();
  await expect(admin.getByText(categoryName)).toBeVisible();

  await admin.goto('/admin/productos/nuevo');
  await admin.getByLabel('Nombre').fill(productName);
  await admin.getByLabel('Descripción').fill('Pijama de algodón peinado.');
  await admin.getByLabel('Material').fill('Algodón');
  await admin.getByLabel('Temporada').selectOption({ label: 'Invierno' });
  await admin.getByLabel('Público').selectOption({ label: 'Mujer' });
  await admin.getByLabel('Categoría').selectOption({ label: categoryName });
  await admin.getByRole('button', { name: 'Crear producto' }).click();
  await expect(admin.getByRole('heading', { name: productName })).toBeVisible();

  await admin.getByLabel('Talles').fill('M');
  await admin.getByLabel('Colores').fill('Rosa, Celeste');
  await admin.getByRole('button', { name: 'Generar variantes' }).click();
  await expect(admin.getByText('Se crearon 2 variantes.')).toBeVisible();

  for (const color of ['Rosa', 'Celeste']) {
    const price = admin.getByLabel(`Precio de M, ${color} ($)`);
    await price.fill('15000');
    await price.press('Enter');
    await expect(price).toHaveValue(/15\.?000/);

    await admin.getByRole('link', { name: `Stock de M, ${color}` }).click();
    await admin.getByLabel('Cantidad (positiva suma, negativa resta)').fill('3');
    await admin.getByRole('button', { name: 'Guardar ajuste' }).click();
    await expect(admin.getByText('Stock actualizado: 3 unidades.')).toBeVisible();
    await admin.getByRole('link', { name: 'Volver al producto' }).click();
  }

  await admin.getByRole('button', { name: 'Publicar' }).click();
  await expect(admin.getByText('Publicado: se ve en la tienda.')).toBeVisible();

  const detail = await (await AdminApi.open(context)).call<ProductForSale>(
    'GET',
    `/api/admin/catalog/products/${admin.url().split('/').pop() ?? ''}`,
    undefined,
    200,
  );
  const celeste = detail.variants.find((variant) => variant.color === 'Celeste')!;
  await context.close();

  // The guest's own browser: the page fixture, never signed in.
  await buyAsGuest(page, detail, celeste, newBuyer());
  await expect(page.getByText(productName)).toBeVisible();
});
