import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { components } from '../../api/schema.d.ts';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

type Row = components['schemas']['OrderSummaryResponse'];

const messages = esAdmin.orderList;

function row(number: number, status: NonNullable<Row['status']>): Row {
  return {
    id: `o-${String(number)}`,
    number,
    createdAt: '2026-10-13T18:00:00Z',
    status,
    buyerName: 'Ana Pérez',
    city: 'Rosario',
    province: 'SANTA_FE',
    totalCents: 4_450_000,
  };
}

describe('order list (UC-18)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given orders, when the list opens, then each shows its number, status, buyer and total, with more on request', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/orders': [
        answer(200, { items: [row(1002, 'PAID')], hasMore: true }),
        answer(200, { items: [row(1001, 'AWAITING_PAYMENT')], hasMore: false }),
      ],
    });
    renderRoute('/admin/pedidos');

    const paid = within((await screen.findByRole('link', { name: '#1002' })).closest('li') as HTMLElement);
    expect(paid.getByText(es.tracking.statuses.PAID)).toBeInTheDocument();
    expect(paid.getByText(/Ana Pérez · Rosario, Santa Fe/)).toBeInTheDocument();
    expect(paid.getByText(/44\.500/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '#1002' })).toHaveAttribute('href', '/admin/pedidos/o-1002');

    await userEvent.click(screen.getByRole('button', { name: esAdmin.showMore }));

    expect(await screen.findByRole('link', { name: '#1001' })).toBeInTheDocument();
    expect(api.callsTo('GET /api/admin/orders').map((call) => call.options.params?.query)).toEqual([
      { page: 0 },
      { page: 1 },
    ]);
  });

  it('given the list of orders, when filtered by paid, then only paid orders are asked for', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/orders': (options: { params?: { query?: { status?: string } } }) =>
        answer(200, {
          items: options.params?.query?.status === 'PAID' ? [row(1002, 'PAID')] : [row(1002, 'PAID'), row(1001, 'EXPIRED')],
          hasMore: false,
        }),
    });
    const { router } = renderRoute('/admin/pedidos');
    await screen.findByRole('link', { name: '#1001' });

    await userEvent.selectOptions(screen.getByLabelText(messages.filter), es.tracking.statuses.PAID);

    expect(await screen.findByRole('link', { name: '#1002' })).toBeInTheDocument();
    await vi.waitFor(() => {
      expect(screen.queryByRole('link', { name: '#1001' })).not.toBeInTheDocument();
    });
    expect(router.state.location.search).toBe('?estado=PAID');
    expect(api.callsTo('GET /api/admin/orders').at(-1)?.options.params?.query).toEqual({ status: 'PAID', page: 0 });
  });

  it('given no orders in a status, when filtered, then it says so', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/orders': answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/pedidos?estado=SHIPPED');

    expect(await screen.findByText(messages.emptyInStatus)).toBeInTheDocument();
    expect(screen.getByLabelText(messages.filter)).toHaveValue('SHIPPED');
  });

  it('given a search typed, when submitted, then it goes trimmed with the status and stays in the address (T-E8-06)', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/orders': answer(200, { items: [row(1002, 'PAID')], hasMore: false }),
    });
    const { router } = renderRoute('/admin/pedidos?estado=PAID');
    const filters = within(await screen.findByRole('search', { name: messages.filters }));

    await userEvent.type(filters.getByLabelText(messages.search), '  pérez  ');
    await userEvent.click(filters.getByRole('button', { name: messages.apply }));

    await vi.waitFor(() => {
      expect(api.callsTo('GET /api/admin/orders').at(-1)?.options.params?.query).toEqual({
        status: 'PAID',
        q: 'pérez',
        page: 0,
      });
    });
    expect(new URLSearchParams(router.state.location.search).get('buscar')).toBe('pérez');
    expect(filters.getByLabelText(messages.search)).toHaveAccessibleDescription(messages.searchHint);
  });

  it('given an order number, when it opens from the address, then it is asked for and the field shows it', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/orders': answer(200, { items: [row(1002, 'PAID')], hasMore: false }),
    });
    renderRoute('/admin/pedidos?buscar=%231002');

    expect(await screen.findByRole('link', { name: '#1002' })).toBeInTheDocument();
    expect(api.callsTo('GET /api/admin/orders')[0]?.options.params?.query).toEqual({ q: '#1002', page: 0 });
    expect(screen.getByLabelText(messages.search)).toHaveValue('#1002');
  });

  it('given a search of one letter, when submitted, then the field says why and nothing is asked for', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/orders': answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/pedidos');
    await screen.findByText(messages.empty);

    await userEvent.type(screen.getByLabelText(messages.search), 'a');
    await userEvent.click(screen.getByRole('button', { name: messages.apply }));

    const field = screen.getByLabelText(messages.search);
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field).toHaveAccessibleDescription(`${messages.searchHint} ${messages.searchInvalid}`);
    expect(api.callsTo('GET /api/admin/orders')).toHaveLength(1);
  });

  it('given dates, when chosen, then they are asked for as store dates; reversed, nothing is asked and it says why', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/orders': answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/pedidos?desde=2026-10-10&hasta=2026-10-10');

    expect(await screen.findByText(messages.emptyFiltered)).toBeInTheDocument();
    expect(api.callsTo('GET /api/admin/orders')[0]?.options.params?.query).toEqual({
      from: '2026-10-10',
      to: '2026-10-10',
      page: 0,
    });

    const from = screen.getByLabelText(messages.from);
    await userEvent.clear(from);
    await userEvent.type(from, '2026-10-11');

    expect(await screen.findByText(messages.invalidPeriod)).toBeInTheDocument();
    expect(from).toHaveAttribute('aria-invalid', 'true');
    expect(api.callsTo('GET /api/admin/orders').map((call) => call.options.params?.query?.from)).not.toContain(
      '2026-10-11',
    );
  });

  it('given a filtered list, when an order opens, then its back link returns to the same filters', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/orders': answer(200, { items: [row(1002, 'PAID')], hasMore: false }),
      'GET /api/admin/orders/{id}': answer(404, {}),
    });
    renderRoute('/admin/pedidos?estado=PAID');

    await userEvent.click(await screen.findByRole('link', { name: '#1002' }));

    expect(await screen.findByRole('link', { name: esAdmin.orderDetail.backToList })).toHaveAttribute(
      'href',
      '/admin/pedidos?estado=PAID',
    );
  });
});
