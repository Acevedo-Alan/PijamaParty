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
});
