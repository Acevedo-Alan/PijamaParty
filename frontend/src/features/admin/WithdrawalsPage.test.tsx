import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { components } from '../../api/schema.d.ts';
import { es } from '../../i18n/es.ts';
import { problem, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

type Row = components['schemas']['WithdrawalSummaryResponse'];

const messages = es.admin.withdrawalList;
const LIST = 'GET /api/admin/withdrawals';
const RESOLVE = 'POST /api/admin/withdrawals/{id}/resolve';

function row(code: string, overrides: Partial<Row> = {}): Row {
  return {
    id: `w-${code}`,
    code,
    orderId: 'o-1',
    orderNumber: 1001,
    orderStatus: 'PAID',
    buyerName: 'Ana Pérez',
    createdAt: '2026-10-13T18:00:00Z',
    outOfTerm: false,
    status: 'RECEIVED',
    resolvedAt: null,
    ...overrides,
  };
}

function card(code: string) {
  return within(screen.getByText(code).closest('li') as HTMLElement);
}

describe('withdrawal requests in the back office (UC-29)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a request, when the list opens, then it shows its code, its order, the buyer and the out-of-term mark', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: [
        answer(200, { items: [row('ARR-7K2M9QXZ', { outOfTerm: true, orderStatus: 'DELIVERED' })], hasMore: true }),
        answer(200, {
          items: [row('ARR-22222222', { status: 'RESOLVED', resolvedAt: '2026-10-14T12:00:00Z' })],
          hasMore: false,
        }),
      ],
    });
    renderRoute('/admin/arrepentimientos');

    await screen.findByText('ARR-7K2M9QXZ');
    const late = card('ARR-7K2M9QXZ');
    expect(late.getByText(messages.outOfTerm)).toBeInTheDocument();
    expect(late.getByText(messages.statuses.RECEIVED)).toBeInTheDocument();
    expect(late.getByRole('link', { name: messages.order(1001) })).toHaveAttribute('href', '/admin/pedidos/o-1');
    expect(late.getByText(new RegExp(es.tracking.statuses.DELIVERED))).toBeInTheDocument();
    expect(late.getByText(/Ana Pérez · pedida el/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: es.admin.showMore }));

    await screen.findByText('ARR-22222222');
    const resolved = card('ARR-22222222');
    expect(resolved.getByText(/Resuelta el/)).toBeInTheDocument();
    expect(resolved.queryByText(messages.outOfTerm)).not.toBeInTheDocument();
    expect(resolved.queryByRole('button', { name: messages.resolve })).not.toBeInTheDocument();
    expect(api.callsTo(LIST).map((call) => call.options.params?.query)).toEqual([{ page: 0 }, { page: 1 }]);
  });

  it('given the list, when filtered by received, then only those are asked for and the filter is in the address', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, { items: [row('ARR-7K2M9QXZ')], hasMore: false }),
    });
    const { router } = renderRoute('/admin/arrepentimientos');
    await screen.findByText('ARR-7K2M9QXZ');

    await userEvent.selectOptions(screen.getByLabelText(messages.filter), messages.statuses.RECEIVED);

    await vi.waitFor(() => {
      expect(api.callsTo(LIST).at(-1)?.options.params?.query).toEqual({ status: 'RECEIVED', page: 0 });
    });
    expect(router.state.location.search).toBe('?estado=RECEIVED');
  });

  it('given no requests in a status, when filtered, then it says so', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [LIST]: answer(200, { items: [], hasMore: false }) });
    renderRoute('/admin/arrepentimientos?estado=RESOLVED');

    expect(await screen.findByText(messages.emptyInStatus)).toBeInTheDocument();
    expect(screen.getByLabelText(messages.filter)).toHaveValue('RESOLVED');
  });

  it('given a received request, when resolved, then it is sent, confirmed and the list is read again', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: [
        answer(200, { items: [row('ARR-7K2M9QXZ')], hasMore: false }),
        answer(200, {
          items: [row('ARR-7K2M9QXZ', { status: 'RESOLVED', resolvedAt: '2026-10-14T12:00:00Z' })],
          hasMore: false,
        }),
      ],
      [RESOLVE]: answer(200, { status: 'RESOLVED', resolvedAt: '2026-10-14T12:00:00Z' }),
    });
    renderRoute('/admin/arrepentimientos');
    await screen.findByText('ARR-7K2M9QXZ');

    await userEvent.click(screen.getByRole('button', { name: messages.resolve }));

    expect(await screen.findByRole('status')).toHaveTextContent(messages.resolved('ARR-7K2M9QXZ'));
    expect(api.callsTo(RESOLVE)[0]?.options.params?.path).toEqual({ id: 'w-ARR-7K2M9QXZ' });
    await vi.waitFor(() => {
      expect(screen.queryByRole('button', { name: messages.resolve })).not.toBeInTheDocument();
    });
  });

  it('given a request someone already resolved, when resolved, then it says so and reads the list again', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, { items: [row('ARR-7K2M9QXZ')], hasMore: false }),
      [RESOLVE]: problem(409, { problem: 'WITHDRAWAL_ALREADY_RESOLVED', resolvedAt: '2026-10-14T12:00:00Z' }),
    });
    renderRoute('/admin/arrepentimientos');
    await screen.findByText('ARR-7K2M9QXZ');

    await userEvent.click(screen.getByRole('button', { name: messages.resolve }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.alreadyResolved);
    await vi.waitFor(() => {
      expect(api.callsTo(LIST)).toHaveLength(2);
    });
  });
});
