import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { category, coupon, problem, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = es.admin.couponList;
const form = es.admin.couponForm;
const verano = coupon('k-1', 'VERANO10', {
  type: 'FIXED_AMOUNT',
  discountValue: 500_000,
  validFrom: '2026-12-01',
  validUntil: '2027-02-28',
  maxUses: 10,
  minimumPurchaseCents: 2_000_000,
  uses: 3,
});
const pijama = coupon('k-2', 'PIJAMA15', { discountValue: 15, active: false, uses: 7 });
const listed = answer(200, [verano, pijama]);

function rowOf(code: string) {
  const row = screen.getByText(code).closest('li');
  if (row === null) {
    throw new Error(`No row for ${code}`);
  }
  return within(row);
}

async function findRow(code: string) {
  await screen.findByText(code);
  return rowOf(code);
}

/** Testing Library collapses the no-break space of formatted pesos in the page; the expected text must match. */
function spaced(text: string) {
  return text.replace(/\s+/g, ' ');
}

/** The "New coupon" form above the list. */
function newCouponForm() {
  return within(screen.getByRole('region', { name: messages.newCoupon }));
}

describe('coupon management (UC-11)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given coupons, when the page opens, then each shows its discount, validity, minimum, uses and state', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, 'GET /api/admin/coupons': listed });

    renderRoute('/admin/cupones');

    const summer = await findRow('VERANO10');
    expect(summer.getByText(spaced(messages.amountOff(formatMoney(500_000))))).toBeInTheDocument();
    expect(summer.getByText(messages.validBetween('01/12/2026', '28/02/2027'))).toBeInTheDocument();
    expect(summer.getByText(spaced(messages.minimum(formatMoney(2_000_000))))).toBeInTheDocument();
    expect(summer.getByText('Usos: 3 de 10')).toBeInTheDocument();
    expect(summer.queryByText(messages.inactive)).not.toBeInTheDocument();
    const pyjama = rowOf('PIJAMA15');
    expect(pyjama.getByText('15 % de descuento')).toBeInTheDocument();
    expect(pyjama.getByText(messages.noExpiry)).toBeInTheDocument();
    expect(pyjama.getByText('Usos: 7')).toBeInTheDocument();
    expect(pyjama.getByText(messages.inactive)).toBeInTheDocument();
    expect(pyjama.getByRole('button', { name: messages.activate })).toBeEnabled();
  });

  it('given a new coupon, when created, then the pesos go as cents, empty fields as null and the list reloads', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': [listed, answer(200, [coupon('k-3', 'OTONO'), verano, pijama])],
      'POST /api/admin/coupons': answer(201, coupon('k-3', 'OTONO')),
    });
    renderRoute('/admin/cupones');
    await screen.findByText('VERANO10');

    const created = newCouponForm();
    await userEvent.type(created.getByLabelText(form.code), 'otono');
    await userEvent.selectOptions(created.getByLabelText(form.type), form.types.FIXED_AMOUNT);
    await userEvent.type(created.getByLabelText(form.amount), '2.500,50');
    await userEvent.type(created.getByLabelText(form.validUntil), '2027-05-31');
    await userEvent.type(created.getByLabelText(form.minimum), '20000');
    await userEvent.click(created.getByRole('button', { name: messages.create }));

    expect(await screen.findByText('OTONO')).toBeInTheDocument();
    expect(api.callsTo('POST /api/admin/coupons')[0]?.options.body).toEqual({
      code: 'otono',
      type: 'FIXED_AMOUNT',
      discountValue: 250_050,
      validFrom: null,
      validUntil: '2027-05-31',
      maxUses: null,
      minimumPurchaseCents: 2_000_000,
      automatic: false,
      scope: { productIds: [], categoryIds: [] },
    });
    expect(created.getByLabelText(form.code)).toHaveValue('');
    expect(api.callsTo('GET /api/admin/coupons')).toHaveLength(2);
  });

  it('given an existing code, when another with the same code is created, then it is rejected with a clear message', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': listed,
      'POST /api/admin/coupons': problem(409, { code: 'VERANO10' }),
    });
    renderRoute('/admin/cupones');
    await screen.findByText('VERANO10');

    const created = newCouponForm();
    await userEvent.type(created.getByLabelText(form.code), 'verano10');
    await userEvent.type(created.getByLabelText(form.percent), '10');
    await userEvent.click(created.getByRole('button', { name: messages.create }));

    expect(await created.findByRole('alert')).toHaveTextContent(form.codeUsed('VERANO10'));
    expect(created.getByLabelText(form.code)).toHaveValue('verano10');
  });

  it('given fields the server refuses, when created, then each one is marked with its message', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': listed,
      'POST /api/admin/coupons': problem(422, { fields: ['DISCOUNT_VALUE', 'VALIDITY'] }),
    });
    renderRoute('/admin/cupones');
    await screen.findByText('VERANO10');

    const created = newCouponForm();
    await userEvent.type(created.getByLabelText(form.code), 'MUCHO');
    await userEvent.type(created.getByLabelText(form.percent), '150');
    await userEvent.click(created.getByRole('button', { name: messages.create }));

    expect(await created.findByRole('alert')).toHaveTextContent(form.invalid);
    expect(created.getByLabelText(form.percent)).toHaveAttribute('aria-invalid', 'true');
    expect(created.getByText(form.errors.DISCOUNT_VALUE)).toBeInTheDocument();
    expect(created.getByLabelText(form.validFrom)).toHaveAttribute('aria-invalid', 'true');
    expect(created.getByText(form.errors.VALIDITY)).toBeInTheDocument();
    expect(created.getByLabelText(form.code)).toHaveAttribute('aria-invalid', 'false');
  });

  it('given a value that is not a number, when created, then it is marked and nothing is sent', async () => {
    const api = stubApi({ 'GET /api/admin/auth/me': signedIn, 'GET /api/admin/coupons': listed });
    renderRoute('/admin/cupones');
    await screen.findByText('VERANO10');

    const created = newCouponForm();
    await userEvent.type(created.getByLabelText(form.code), 'VERANO20');
    await userEvent.type(created.getByLabelText(form.percent), 'diez');
    await userEvent.type(created.getByLabelText(form.maxUses), '-3');
    await userEvent.click(created.getByRole('button', { name: messages.create }));

    expect(await created.findByRole('alert')).toHaveTextContent(form.invalid);
    expect(created.getByText(form.errors.DISCOUNT_VALUE)).toBeInTheDocument();
    expect(created.getByText(form.errors.MAX_USES)).toBeInTheDocument();
    expect(api.callsTo('POST /api/admin/coupons')).toHaveLength(0);
  });

  it('given a coupon with recorded uses, when deletion is attempted, then only deactivating it is offered', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': listed,
      'DELETE /api/admin/coupons/{id}': problem(409),
    });
    renderRoute('/admin/cupones');

    await userEvent.click((await findRow('VERANO10')).getByRole('button', { name: messages.delete }));

    expect(confirm).toHaveBeenCalledWith(messages.confirmDelete('VERANO10'));
    expect(await screen.findByText(messages.hasUses)).toHaveAttribute('role', 'alert');
    expect(rowOf('VERANO10').getByRole('button', { name: messages.deactivate })).toBeEnabled();
  });

  it('given a confirmation refused, when deleting, then nothing is sent', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const api = stubApi({ 'GET /api/admin/auth/me': signedIn, 'GET /api/admin/coupons': listed });
    renderRoute('/admin/cupones');

    await userEvent.click((await findRow('VERANO10')).getByRole('button', { name: messages.delete }));

    expect(api.callsTo('DELETE /api/admin/coupons/{id}')).toHaveLength(0);
  });

  it('given an active coupon, when deactivated, then it is sent to the server', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': listed,
      'POST /api/admin/coupons/{id}/deactivate': answer(200, { ...verano, active: false }),
    });
    renderRoute('/admin/cupones');

    await userEvent.click((await findRow('VERANO10')).getByRole('button', { name: messages.deactivate }));

    expect(await screen.findByText(es.admin.saved)).toHaveAttribute('role', 'status');
    expect(api.callsTo('POST /api/admin/coupons/{id}/deactivate')[0]?.options.params?.path).toEqual({ id: 'k-1' });
  });

  it('given a coupon, when edited in place, then the form starts with its data and the whole coupon is sent', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': listed,
      'PUT /api/admin/coupons/{id}': answer(200, { ...verano, maxUses: 20 }),
    });
    renderRoute('/admin/cupones');

    await userEvent.click((await findRow('VERANO10')).getByRole('button', { name: messages.edit }));
    const edited = within(screen.getByText(messages.editing('VERANO10')).closest('li') as HTMLElement);
    expect(edited.getByLabelText(form.code)).toHaveValue('VERANO10');
    expect(edited.getByLabelText(form.amount)).toHaveValue('5000');
    expect(edited.getByLabelText(form.validFrom)).toHaveValue('2026-12-01');
    expect(edited.getByLabelText(form.minimum)).toHaveValue('20000');
    await userEvent.clear(edited.getByLabelText(form.maxUses));
    await userEvent.type(edited.getByLabelText(form.maxUses), '20');
    await userEvent.click(edited.getByRole('button', { name: es.admin.save }));

    expect(await screen.findByText(es.admin.saved)).toHaveAttribute('role', 'status');
    expect(screen.queryByText(messages.editing('VERANO10'))).not.toBeInTheDocument();
    expect(api.callsTo('PUT /api/admin/coupons/{id}')[0]?.options).toMatchObject({
      params: { path: { id: 'k-1' } },
      body: {
        code: 'VERANO10',
        type: 'FIXED_AMOUNT',
        discountValue: 500_000,
        validFrom: '2026-12-01',
        validUntil: '2027-02-28',
        maxUses: 20,
        minimumPurchaseCents: 2_000_000,
      },
    });
  });
});

describe('automatic coupons (UC-11, UC-23)', () => {
  const catalog = {
    'GET /api/admin/catalog/categories': answer(200, [category('c-1', 'Pijamas'), category('c-2', 'Camisones')]),
    'GET /api/admin/catalog/products': answer(200, {
      items: [
        { id: 'p-1', categoryId: 'c-1', name: 'Pijama Luna', slug: 'pijama-luna', published: true, variantCount: 2 },
        { id: 'p-2', categoryId: 'c-2', name: 'Camisón Sol', slug: 'camison-sol', published: true, variantCount: 1 },
      ],
      hasMore: false,
    }),
  };
  const automatic = coupon('k-4', 'VERANO10', {
    automatic: true,
    scope: { productIds: [], categoryIds: ['c-1'] },
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given an automatic coupon, when it is created with a scope of one category, then the list shows it as automatic with that category', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': [answer(200, []), answer(200, [automatic])],
      'POST /api/admin/coupons': answer(201, automatic),
      ...catalog,
    });
    renderRoute('/admin/cupones');
    await screen.findByText(messages.empty);

    const created = newCouponForm();
    expect(created.queryByRole('group', { name: form.scope })).not.toBeInTheDocument();
    await userEvent.click(created.getByRole('checkbox', { name: form.automatic }));
    await userEvent.type(created.getByLabelText(form.code), 'verano10');
    await userEvent.type(created.getByLabelText(form.percent), '10');
    const scope = within(created.getByRole('group', { name: form.scope }));
    await userEvent.click(await scope.findByRole('checkbox', { name: 'Pijamas' }));
    expect(scope.getByText(form.scopeCount(1))).toBeInTheDocument();
    await userEvent.click(created.getByRole('button', { name: messages.create }));

    const row = await findRow('VERANO10');
    expect(row.getByText(messages.automatic)).toBeInTheDocument();
    expect(row.getByText(messages.scope('Pijamas'))).toBeInTheDocument();
    expect(api.callsTo('POST /api/admin/coupons')[0]?.options.body).toMatchObject({
      code: 'verano10',
      automatic: true,
      scope: { productIds: [], categoryIds: ['c-1'] },
    });
  });

  it('given a scope picked and then automatic unticked, when created, then it goes as a code coupon without a scope', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': answer(200, []),
      'POST /api/admin/coupons': answer(201, coupon('k-5', 'OTONO')),
      ...catalog,
    });
    renderRoute('/admin/cupones');
    await screen.findByText(messages.empty);

    const created = newCouponForm();
    await userEvent.click(created.getByRole('checkbox', { name: form.automatic }));
    await userEvent.click(await created.findByRole('checkbox', { name: 'Pijama Luna' }));
    await userEvent.click(created.getByRole('checkbox', { name: form.automatic }));
    await userEvent.type(created.getByLabelText(form.code), 'OTONO');
    await userEvent.type(created.getByLabelText(form.percent), '10');
    await userEvent.click(created.getByRole('button', { name: messages.create }));

    await screen.findByText(es.admin.saved);
    expect(api.callsTo('POST /api/admin/coupons')[0]?.options.body).toMatchObject({
      automatic: false,
      scope: { productIds: [], categoryIds: [] },
    });
  });

  it('given products, when the filter is typed, then only the matching products are offered', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, 'GET /api/admin/coupons': answer(200, []), ...catalog });
    renderRoute('/admin/cupones');
    await screen.findByText(messages.empty);

    const created = newCouponForm();
    await userEvent.click(created.getByRole('checkbox', { name: form.automatic }));
    await userEvent.type(await created.findByLabelText(form.productFilter), 'camisón');

    expect(created.getByRole('checkbox', { name: 'Camisón Sol' })).toBeInTheDocument();
    expect(created.queryByRole('checkbox', { name: 'Pijama Luna' })).not.toBeInTheDocument();
    await userEvent.type(created.getByLabelText(form.productFilter), 'xyz');
    expect(created.getByText(form.noProducts)).toBeInTheDocument();
  });

  it('given automatic coupons, when listed, then the scope shows by name, a removed target as such and none as the whole cart', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': answer(200, [
        coupon('k-6', 'MIXTO', { automatic: true, scope: { productIds: ['p-2', 'p-9'], categoryIds: ['c-1'] } }),
        coupon('k-7', 'TODO', { automatic: true }),
        coupon('k-8', 'CODIGO'),
      ]),
      ...catalog,
    });
    renderRoute('/admin/cupones');

    const mixed = await findRow('MIXTO');
    expect(await mixed.findByText(messages.scope(`Pijamas, Camisón Sol, ${messages.removed}`))).toBeInTheDocument();
    expect(rowOf('TODO').getByText(messages.wholeCart)).toBeInTheDocument();
    expect(rowOf('CODIGO').queryByText(messages.automatic)).not.toBeInTheDocument();
    expect(rowOf('CODIGO').queryByText(messages.wholeCart)).not.toBeInTheDocument();
  });

  it('given an automatic coupon, when edited, then automatic cannot change and its scope can', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': answer(200, [automatic]),
      'PUT /api/admin/coupons/{id}': answer(200, automatic),
      ...catalog,
    });
    renderRoute('/admin/cupones');

    await userEvent.click((await findRow('VERANO10')).getByRole('button', { name: messages.edit }));
    const edited = within(screen.getByText(messages.editing('VERANO10')).closest('li') as HTMLElement);
    expect(edited.queryByRole('checkbox', { name: form.automatic })).not.toBeInTheDocument();
    expect(await edited.findByRole('checkbox', { name: 'Pijamas' })).toBeChecked();
    await userEvent.click(edited.getByRole('checkbox', { name: 'Camisón Sol' }));
    await userEvent.click(edited.getByRole('button', { name: es.admin.save }));

    await screen.findByText(es.admin.saved);
    expect(api.callsTo('PUT /api/admin/coupons/{id}')[0]?.options.body).toMatchObject({
      automatic: true,
      scope: { productIds: ['p-2'], categoryIds: ['c-1'] },
    });
  });

  it('given a scope the server refuses, when created, then the scope is marked with its message', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/coupons': answer(200, []),
      'POST /api/admin/coupons': problem(422, { fields: ['SCOPE'] }),
      ...catalog,
    });
    renderRoute('/admin/cupones');
    await screen.findByText(messages.empty);

    const created = newCouponForm();
    await userEvent.click(created.getByRole('checkbox', { name: form.automatic }));
    await userEvent.type(created.getByLabelText(form.code), 'VERANO10');
    await userEvent.type(created.getByLabelText(form.percent), '10');
    await userEvent.click(created.getByRole('button', { name: messages.create }));

    expect(await created.findByText(form.errors.SCOPE)).toBeInTheDocument();
    expect(created.getAllByRole('alert').some((alert) => alert.textContent === form.invalid)).toBe(true);
  });
});
