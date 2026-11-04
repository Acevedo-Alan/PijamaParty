import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { category, connectionLost, problem, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = esAdmin.stockOverview;
const STOCK = 'GET /api/admin/catalog/stock';
const ADJUST = 'POST /api/admin/stock-adjustments';
const SUMMER = '6f1c2b3a-0000-4000-8000-0000000000c1';
const categories = answer(200, [category(SUMMER, 'Verano')]);

function row(size: string, onHand: number, reserved: number, overrides: Record<string, unknown> = {}) {
  return {
    variantId: `v-${size}`,
    productId: 'p-1',
    productName: 'Pijama Luna',
    size,
    color: 'Rosa',
    sku: `PP-${size}`,
    published: true,
    onHand,
    reserved,
    available: onHand - reserved,
    ...overrides,
  };
}

function rowOf(size: string) {
  const name = messages.adjust('Pijama Luna', messages.variant(size, 'Rosa'));
  return within(screen.getByRole('form', { name }).closest('li') as HTMLElement);
}

function figure(card: ReturnType<typeof rowOf>, label: string) {
  return card.getByText(label).nextElementSibling;
}

async function press(size: string, button: string, quantity: string, note = '') {
  const card = rowOf(size);
  const field = card.getByLabelText(messages.quantity);
  await userEvent.clear(field);
  await userEvent.type(field, quantity);
  if (note !== '') {
    await userEvent.type(card.getByLabelText(messages.note), note);
  }
  await userEvent.click(card.getByRole('button', { name: button }));
}

describe('Stock screen (UC-07, T-E8-09)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given variants, when the screen opens, then each row shows its units, state, SKU and history link', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: [
        answer(200, { items: [row('S', 0, 0), row('M', 4, 3, { published: false })], hasMore: true }),
        answer(200, { items: [row('L', 9, 0)], hasMore: false }),
      ],
    });
    renderRoute('/admin/stock');

    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });
    const medium = rowOf('M');
    expect(figure(medium, messages.onHand)).toHaveTextContent('4');
    expect(figure(medium, messages.reserved)).toHaveTextContent('3');
    expect(figure(medium, messages.available)).toHaveTextContent('1');
    expect(medium.getByText(messages.hidden)).toBeInTheDocument();
    expect(medium.getByText('PP-M')).toHaveAttribute('translate', 'no');
    expect(medium.getByRole('link', { name: messages.history })).toHaveAttribute(
      'href',
      '/admin/productos/p-1/variantes/v-M/stock',
    );
    expect(medium.getByLabelText(messages.quantity)).toHaveValue(1);

    await userEvent.click(screen.getByRole('button', { name: esAdmin.showMore }));

    expect(await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('L', 'Rosa')) }))
      .toBeInTheDocument();
    expect(api.callsTo(STOCK).map((call) => call.options.params?.query)).toEqual([{ page: 0 }, { page: 1 }]);
  });

  it('given filters in the address, when the screen opens, then they are asked for; refused values are left out', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [], hasMore: false }),
    });
    renderRoute(`/admin/stock?filtro=LOW&buscar=luna&categoria=${SUMMER}`);

    expect(await screen.findByText(messages.emptyFiltered)).toBeInTheDocument();
    expect(api.callsTo(STOCK)[0]?.options.params?.query).toEqual({
      page: 0,
      filter: 'LOW',
      q: 'luna',
      category: SUMMER,
    });
    expect(screen.getByLabelText(messages.filter)).toHaveValue('LOW');
    expect(screen.getByLabelText(messages.search)).toHaveValue('luna');
  });

  it('given values the server would refuse in the address, when the screen opens, then they are left out', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/stock?filtro=SOME&buscar=a&categoria=verano');

    expect(await screen.findByText(messages.empty)).toBeInTheDocument();
    expect(api.callsTo(STOCK)[0]?.options.params?.query).toEqual({ page: 0 });
  });

  it('given the filter changed to sold out and a search, when applied, then both are asked for and kept in the address', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('S', 0, 0)], hasMore: false }),
    });
    const { router } = renderRoute('/admin/stock');
    const filters = within(await screen.findByRole('search', { name: messages.filters }));

    await userEvent.selectOptions(filters.getByLabelText(messages.filter), messages.filterNames.OUT);
    await userEvent.type(filters.getByLabelText(messages.search), ' pp-s ');
    await userEvent.click(filters.getByRole('button', { name: messages.apply }));

    await vi.waitFor(() => {
      expect(api.callsTo(STOCK).at(-1)?.options.params?.query).toEqual({ page: 0, filter: 'OUT', q: 'pp-s' });
    });
    const address = new URLSearchParams(router.state.location.search);
    expect(address.get('filtro')).toBe('OUT');
    expect(address.get('buscar')).toBe('pp-s');
  });

  it('given a search of one letter, when submitted, then the field says why, takes the focus and nothing is asked', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/stock');
    await screen.findByText(messages.empty);

    await userEvent.type(screen.getByLabelText(messages.search), 'a');
    await userEvent.click(screen.getByRole('button', { name: messages.apply }));

    const field = screen.getByLabelText(messages.search);
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field).toHaveFocus();
    expect(api.callsTo(STOCK)).toHaveLength(1);
  });

  it('given a row, when 3 are added with a note, then they are saved signed and the row shows the new units in place', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('S', 0, 0), row('M', 4, 3)], hasMore: false }),
      [ADJUST]: answer(201, { variantId: 'v-M', onHand: 7 }),
    });
    renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });

    await press('M', `+ ${messages.add}`, '3', 'Llegó el pedido');

    const medium = rowOf('M');
    expect(await medium.findByRole('status')).toHaveTextContent(esAdmin.stock.adjusted(7));
    expect(figure(medium, messages.onHand)).toHaveTextContent('7');
    expect(figure(medium, messages.available)).toHaveTextContent('4');
    expect(api.callsTo(ADJUST)[0]?.options.body).toEqual({ variantId: 'v-M', quantity: 3, note: 'Llegó el pedido' });
    expect(api.callsTo(STOCK)).toHaveLength(1);
    const forms = screen.getAllByRole('form');
    expect(forms[1]).toHaveAccessibleName(messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')));
  });

  it('given 4 on hand and 3 reserved, when 2 are subtracted, then it is refused as UC-07 refuses it and the row stays', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('M', 4, 3)], hasMore: false }),
      [ADJUST]: problem(409, { problem: 'WOULD_GO_BELOW_RESERVED', onHand: 4, reserved: 3 }),
    });
    renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });

    await press('M', `− ${messages.subtract}`, '2');

    const medium = rowOf('M');
    expect(await medium.findByRole('alert')).toHaveTextContent(esAdmin.stock.wouldGoBelowReserved(3, 4));
    expect(figure(medium, messages.onHand)).toHaveTextContent('4');
    expect(figure(medium, messages.available)).toHaveTextContent('1');
    expect(api.callsTo(ADJUST)[0]?.options.body).toEqual({ variantId: 'v-M', quantity: -2 });
  });

  it('given a lost connection, when the same button is pressed again, then the same key goes; another quantity gets a new one', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('M', 4, 0)], hasMore: false }),
      [ADJUST]: [connectionLost(), answer(201, { variantId: 'v-M', onHand: 7 }), answer(201, { variantId: 'v-M', onHand: 8 })],
    });
    renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });

    await press('M', `+ ${messages.add}`, '3');
    expect(await rowOf('M').findByRole('alert')).toHaveTextContent(messages.connectionLost);
    await userEvent.click(rowOf('M').getByRole('button', { name: `+ ${messages.add}` }));
    expect(await rowOf('M').findByRole('status')).toHaveTextContent(esAdmin.stock.adjusted(7));
    await press('M', `+ ${messages.add}`, '1');

    const keys = api.callsTo(ADJUST).map((call) => call.options.params?.header?.['Idempotency-Key']);
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/);
    expect(keys[1]).toBe(keys[0]);
    expect(keys[2]).not.toBe(keys[0]);
  });

  it('given a quantity that is not a whole number above 0, when a button is pressed, then the field says why and nothing is sent', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('M', 4, 0)], hasMore: false }),
    });
    renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });

    await press('M', `+ ${messages.add}`, '0');

    const field = rowOf('M').getByLabelText(messages.quantity);
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field).toHaveAccessibleDescription(messages.quantityInvalid);
    expect(field).toHaveFocus();
    expect(api.callsTo(ADJUST)).toHaveLength(0);
  });

  it('given an adjustment on its way, when the row shows, then the pressed button says it is saving', async () => {
    let answerAdjustment: (value: unknown) => void = () => undefined;
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('M', 1200, 0)], hasMore: false }),
      [ADJUST]: () =>
        new Promise((resolve) => {
          answerAdjustment = resolve;
        }),
    });
    renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });
    expect(figure(rowOf('M'), messages.onHand)).toHaveTextContent('1.200');

    await press('M', `+ ${messages.add}`, '2');

    expect(rowOf('M').getByRole('button', { name: esAdmin.stock.submitting })).toBeDisabled();
    expect(rowOf('M').getByRole('button', { name: `− ${messages.subtract}` })).toBeDisabled();
    answerAdjustment(answer(201, { variantId: 'v-M', onHand: 1202 }));
    expect(await rowOf('M').findByRole('status')).toHaveTextContent(esAdmin.stock.adjusted(1202));
    expect(figure(rowOf('M'), messages.onHand)).toHaveTextContent('1.202');
  });

  it('given "Ver más" failing, when pressed, then the rows read so far stay and it says why', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: [answer(200, { items: [row('M', 4, 0)], hasMore: true }), connectionLost()],
    });
    renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });

    await userEvent.click(screen.getByRole('button', { name: esAdmin.showMore }));

    expect(await screen.findByText(esAdmin.loadError)).toHaveAttribute('role', 'alert');
    expect(rowOf('M').getByText(messages.onHand)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: esAdmin.showMore })).toBeEnabled();
  });

  it('given the screen filtered by low stock, when a history opens and its back link is followed, then the filters stay', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('M', 2, 0)], hasMore: false }),
      'GET /api/admin/catalog/products/{id}': answer(404, {}),
      'GET /api/admin/variants/{id}/stock-movements': answer(200, { variantId: 'v-M', onHand: 2, items: [], hasMore: false }),
    });
    const { router } = renderRoute('/admin/stock?filtro=LOW');

    await userEvent.click(await screen.findByRole('link', { name: messages.history }));
    const back = await screen.findByRole('link', { name: messages.backToStock });
    expect(back).toHaveAttribute('href', '/admin/stock?filtro=LOW');
    await userEvent.click(back);

    expect(router.state.location.pathname).toBe('/admin/stock');
    expect(router.state.location.search).toBe('?filtro=LOW');
  });

  it('given a history opened from its address, when shown, then its back link goes to the product', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': answer(404, {}),
      'GET /api/admin/variants/{id}/stock-movements': answer(200, { variantId: 'v-M', onHand: 2, items: [], hasMore: false }),
    });
    renderRoute('/admin/productos/p-1/variantes/v-M/stock');

    expect(await screen.findByRole('link', { name: esAdmin.stock.backToProduct })).toHaveAttribute(
      'href',
      '/admin/productos/p-1',
    );
  });

  it('given −2 saved on 5 on hand, when "Deshacer" is pressed, then +2 is saved with a new key and the row shows 5 again', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('M', 5, 0)], hasMore: false }),
      [ADJUST]: [answer(201, { variantId: 'v-M', onHand: 3 }), answer(201, { variantId: 'v-M', onHand: 5 })],
    });
    renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });

    await press('M', `− ${messages.subtract}`, '2', 'Error de carga');
    expect(await rowOf('M').findByRole('status')).toHaveTextContent(esAdmin.stock.adjusted(3));
    expect(rowOf('M').getByLabelText(messages.quantity)).toHaveValue(1);
    expect(rowOf('M').getByLabelText(messages.note)).toHaveValue('');
    await userEvent.click(rowOf('M').getByRole('button', { name: messages.undo(2) }));

    expect(await rowOf('M').findByRole('status')).toHaveTextContent(esAdmin.stock.adjusted(5));
    expect(figure(rowOf('M'), messages.onHand)).toHaveTextContent('5');
    expect(rowOf('M').queryByRole('button', { name: messages.undo(2) })).not.toBeInTheDocument();
    const calls = api.callsTo(ADJUST);
    expect(calls.map((call) => call.options.body)).toEqual([
      { variantId: 'v-M', quantity: -2, note: 'Error de carga' },
      { variantId: 'v-M', quantity: 2 },
    ]);
    expect(calls[1]?.options.params?.header?.['Idempotency-Key']).not.toBe(
      calls[0]?.options.params?.header?.['Idempotency-Key'],
    );
  });

  it('given an addition saved, when the row shows, then there is nothing to undo', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('M', 5, 0)], hasMore: false }),
      [ADJUST]: answer(201, { variantId: 'v-M', onHand: 6 }),
    });
    renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });

    await press('M', `+ ${messages.add}`, '1');

    expect(await rowOf('M').findByRole('status')).toHaveTextContent(esAdmin.stock.adjusted(6));
    expect(rowOf('M').queryByRole('button', { name: /Deshacer/ })).not.toBeInTheDocument();
  });

  it('given a note typed and not saved, when the screen is left, then it asks first and staying keeps the note', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(404, {}),
      'GET /api/admin/variants/{id}/stock-movements': answer(200, { variantId: 'v-M', onHand: 5, items: [], hasMore: false }),
      [STOCK]: answer(200, { items: [row('M', 5, 0)], hasMore: false }),
    });
    const { router } = renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });
    await userEvent.type(rowOf('M').getByLabelText(messages.note), 'Falta contar');
    const history = rowOf('M').getByRole('link', { name: messages.history });

    await userEvent.click(history);

    expect(confirm).toHaveBeenCalledWith(esAdmin.stockOverview.unsavedChanges);
    expect(router.state.location.pathname).toBe('/admin/stock');
    expect(rowOf('M').getByLabelText(messages.note)).toHaveValue('Falta contar');

    await userEvent.click(history);

    await vi.waitFor(() => {
      expect(router.state.location.pathname).toBe('/admin/productos/p-1/variantes/v-M/stock');
    });
  });

  it('given nothing typed, when the filter changes, then it does not ask', async () => {
    const confirm = vi.spyOn(window, 'confirm');
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('M', 5, 0)], hasMore: false }),
    });
    const { router } = renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });

    await userEvent.selectOptions(screen.getByLabelText(messages.filter), messages.filterNames.LOW);

    expect(router.state.location.search).toBe('?filtro=LOW');
    expect(confirm).not.toHaveBeenCalled();
  });

  it('given a saved subtraction, when a later attempt is refused, then "Deshacer" stays; after an undo the focus is on the quantity', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('M', 5, 0)], hasMore: false }),
      [ADJUST]: [
        answer(201, { variantId: 'v-M', onHand: 3 }),
        problem(409, { problem: 'WOULD_GO_NEGATIVE', onHand: 3 }),
        answer(201, { variantId: 'v-M', onHand: 5 }),
      ],
    });
    renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });

    await press('M', `− ${messages.subtract}`, '2');
    expect(await rowOf('M').findByRole('status')).toHaveTextContent(esAdmin.stock.adjusted(3));
    await press('M', `− ${messages.subtract}`, '9');
    expect(await rowOf('M').findByRole('alert')).toHaveTextContent(esAdmin.stock.wouldGoNegative(3));
    await userEvent.click(rowOf('M').getByRole('button', { name: messages.undo(2) }));

    expect(await rowOf('M').findByRole('status')).toHaveTextContent(esAdmin.stock.adjusted(5));
    expect(rowOf('M').getByLabelText(messages.quantity)).toHaveFocus();
  });

  it('given a quantity typed and not saved, when the filter changes, then it asks first and staying keeps the filter', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      [STOCK]: answer(200, { items: [row('M', 5, 0)], hasMore: false }),
    });
    const { router } = renderRoute('/admin/stock');
    await screen.findByRole('form', { name: messages.adjust('Pijama Luna', messages.variant('M', 'Rosa')) });
    const field = rowOf('M').getByLabelText(messages.quantity);
    await userEvent.clear(field);
    await userEvent.type(field, '4');

    await userEvent.selectOptions(screen.getByLabelText(messages.filter), messages.filterNames.OUT);

    expect(confirm).toHaveBeenCalledWith(esAdmin.stockOverview.unsavedChanges);
    expect(router.state.location.search).toBe('');
    expect(screen.getByLabelText(messages.filter)).toHaveValue('');
    expect(rowOf('M').getByLabelText(messages.quantity)).toHaveValue(4);
    expect(api.callsTo(STOCK)).toHaveLength(1);
  });
});
