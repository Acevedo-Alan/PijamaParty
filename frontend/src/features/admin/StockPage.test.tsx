import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { adminProduct, connectionLost, problem, signedIn, stubApi, variant } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = esAdmin.stock;
const PATH = '/admin/productos/p-1/variantes/v-M-Rosa/stock';
const product = answer(200, adminProduct({ sizes: ['M'], colors: ['Rosa'], variants: [variant('M', 'Rosa')] }));

function movement(id: string, quantity: number, overrides: Record<string, unknown> = {}) {
  return {
    id,
    type: 'ADJUSTMENT',
    quantity,
    note: null,
    userId: 'admin-1',
    userName: 'Dueña',
    createdAt: '2026-10-11T17:30:00Z',
    ...overrides,
  };
}

function history(onHand: number, items: unknown[], hasMore = false) {
  return answer(200, { variantId: 'v-M-Rosa', onHand, items, hasMore });
}

async function adjust(quantity: string, note = '') {
  const field = await screen.findByLabelText(messages.quantity);
  await userEvent.clear(field);
  await userEvent.type(field, quantity);
  if (note !== '') {
    await userEvent.type(screen.getByLabelText(messages.note), note);
  }
  await userEvent.click(screen.getByRole('button', { name: messages.submit }));
}

describe('stock management (UC-07)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a variant, when its stock opens, then the stock and each movement with date, user, amount and reason show', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': product,
      'GET /api/admin/variants/{id}/stock-movements': history(3, [
        movement('m-2', -2, { note: 'Fallada' }),
        movement('m-1', 5, { type: 'INITIAL_LOAD', userName: null }),
      ]),
    });

    renderRoute(PATH);

    expect(await screen.findByText(messages.onHand(3))).toBeInTheDocument();
    expect(screen.getByText(messages.variant('Pijama Luna', 'M', 'Rosa'), { exact: false })).toBeInTheDocument();
    const [latest, first] = within(screen.getByRole('region', { name: messages.history })).getAllByRole('listitem');
    expect(latest).toHaveTextContent('−2');
    expect(latest).toHaveTextContent('Fallada');
    expect(latest).toHaveTextContent(/11\/10\/2?0?26,? 14:30/);
    expect(latest).toHaveTextContent('Dueña');
    expect(first).toHaveTextContent('+5');
    expect(first).toHaveTextContent(messages.types.INITIAL_LOAD);
    expect(first).toHaveTextContent(messages.unknownUser);
  });

  it('given stock 3, when subtracting 5, then it is rejected with the current stock', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': product,
      'GET /api/admin/variants/{id}/stock-movements': history(3, [movement('m-1', 3)]),
      'POST /api/admin/stock-adjustments': problem(409, { problem: 'WOULD_GO_NEGATIVE', onHand: 3 }),
    });
    renderRoute(PATH);

    await adjust('-5');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.wouldGoNegative(3));
  });

  it('given units reserved by orders, when subtracting them, then the reserved units are shown', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': product,
      'GET /api/admin/variants/{id}/stock-movements': history(3, [movement('m-1', 3)]),
      'POST /api/admin/stock-adjustments': problem(409, {
        problem: 'WOULD_GO_BELOW_RESERVED',
        onHand: 3,
        reserved: 2,
      }),
    });
    renderRoute(PATH);

    await adjust('-2');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.wouldGoBelowReserved(2, 3));
    expect(messages.wouldGoBelowReserved(2, 3)).toBe(
      'No se puede: hay 2 unidades reservadas por pedidos pendientes de pago. Hay 3.',
    );
    expect(messages.wouldGoBelowReserved(1, 3)).toContain('hay 1 unidad reservada por');
  });

  it('given a valid adjustment, when saved, then the stock and the history reload', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': product,
      'GET /api/admin/variants/{id}/stock-movements': [
        history(0, []),
        history(4, [movement('m-1', 4, { type: 'INITIAL_LOAD', note: 'Llegó el pedido' })]),
      ],
      'POST /api/admin/stock-adjustments': answer(201, { variantId: 'v-M-Rosa', onHand: 4 }),
    });
    renderRoute(PATH);

    await adjust('4', 'Llegó el pedido');

    expect(await screen.findByText(messages.adjusted(4))).toBeInTheDocument();
    expect(await screen.findByText(messages.onHand(4))).toBeInTheDocument();
    expect(screen.getByText('Llegó el pedido')).toBeInTheDocument();
    expect(screen.getByLabelText(messages.quantity)).toHaveValue(null);
    expect(api.callsTo('POST /api/admin/stock-adjustments')[0]?.options.body).toEqual({
      variantId: 'v-M-Rosa',
      quantity: 4,
      note: 'Llegó el pedido',
    });
  });

  it('given an adjustment whose response was lost, when retried, then the same key is sent so the stock changes once', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': product,
      'GET /api/admin/variants/{id}/stock-movements': history(3, []),
      'POST /api/admin/stock-adjustments': [connectionLost(), answer(200, { variantId: 'v-M-Rosa', onHand: 5 })],
    });
    renderRoute(PATH);

    await adjust('2');
    expect(await screen.findByRole('alert')).toHaveTextContent(messages.connectionLost);
    await userEvent.click(screen.getByRole('button', { name: messages.submit }));

    expect(await screen.findByText(messages.adjusted(5))).toBeInTheDocument();
    const [first, retry] = api.callsTo('POST /api/admin/stock-adjustments');
    expect(first?.options.params?.header?.['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(retry?.options.params?.header?.['Idempotency-Key']).toBe(
      first?.options.params?.header?.['Idempotency-Key'],
    );
  });

  it('given a lost answer, when the quantity changes before retrying, then a new key is sent', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': product,
      'GET /api/admin/variants/{id}/stock-movements': history(3, []),
      'POST /api/admin/stock-adjustments': connectionLost(),
    });
    renderRoute(PATH);

    await adjust('2');
    await screen.findByRole('alert');
    await adjust('3');

    await vi.waitFor(() => {
      expect(api.callsTo('POST /api/admin/stock-adjustments')).toHaveLength(2);
    });
    const [first, second] = api.callsTo('POST /api/admin/stock-adjustments');
    expect(second?.options.params?.header?.['Idempotency-Key']).not.toBe(
      first?.options.params?.header?.['Idempotency-Key'],
    );
  });

  it('given the − and + buttons, when tapped, then the quantity steps by one', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': product,
      'GET /api/admin/variants/{id}/stock-movements': history(3, []),
    });
    renderRoute(PATH);

    await userEvent.click(await screen.findByRole('button', { name: messages.decrease }));
    await userEvent.click(screen.getByRole('button', { name: messages.decrease }));
    await userEvent.click(screen.getByRole('button', { name: messages.increase }));

    expect(screen.getByLabelText(messages.quantity)).toHaveValue(-1);
  });

  it('given a quantity out of range, when saved, then the server reason is shown', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': product,
      'GET /api/admin/variants/{id}/stock-movements': history(3, []),
      'POST /api/admin/stock-adjustments': problem(422, { problem: 'INVALID_QUANTITY' }),
    });
    renderRoute(PATH);

    await adjust('20000');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.problems.INVALID_QUANTITY);
  });

  it('given more than 50 movements, when "See more" is tapped, then the next page loads', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': product,
      'GET /api/admin/variants/{id}/stock-movements': [
        history(3, [movement('m-2', 1)], true),
        history(3, [movement('m-1', 2)], false),
      ],
    });
    renderRoute(PATH);

    await userEvent.click(await screen.findByRole('button', { name: esAdmin.showMore }));

    expect(await screen.findByText('+2')).toBeInTheDocument();
    expect(
      api.callsTo('GET /api/admin/variants/{id}/stock-movements').map((call) => call.options.params?.query),
    ).toEqual([{ page: 0 }, { page: 1 }]);
  });

  it('given an unknown variant, when its stock opens, then it says so', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': product,
      'GET /api/admin/variants/{id}/stock-movements': problem(404),
    });

    renderRoute(PATH);

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.variantNotFound);
    expect(screen.queryByLabelText(messages.quantity)).not.toBeInTheDocument();
  });
});
