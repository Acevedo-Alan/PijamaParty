import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { components } from '../../api/schema.d.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

type Body = components['schemas']['DashboardResponse'];

const messages = esAdmin.dashboard;
const DASHBOARD = 'GET /api/admin/dashboard';
const COUNTS = 'GET /api/admin/dashboard/counts';

function period(amountCents: number, orders: number, variation: number | null) {
  return { amountCents, orders, variation };
}

function days(count: number) {
  return Array.from({ length: count }, (_, index) => ({
    period: `2026-10-${String(index + 1).padStart(2, '0')}`,
    amountCents: index === 14 ? 15_000_000 : 0,
    orders: index === 14 ? 1 : 0,
  }));
}

function dashboard(overrides: Partial<Body> = {}): Body {
  return {
    todo: { paidOrders: 2, preparingOrders: 0, receivedWithdrawals: 1, pendingReviews: 3, emailAlerts: 0,
      soldOutVariants: 1 },
    sales: {
      today: period(15_000_000, 1, 50),
      lastWeek: period(25_000_000, 2, null),
      month: period(30_000_000, 3, -12),
      averageTicketCents: 10_000_000,
    },
    chart: { kind: 'DAYS', points: days(30) },
    lowStock: {
      threshold: 3,
      count: 12,
      items: [
        { variantId: 'v-1', productId: 'p-1', productName: 'Pijama Luna', size: 'M', color: 'Rosa', available: 0 },
        { variantId: 'v-2', productId: 'p-1', productName: 'Pijama Luna', size: 'L', color: 'Rosa', available: 2 },
      ],
    },
    bestSellers: [{ productSlug: 'luna', productName: 'Pijama Luna', units: 5, amountCents: 10_000_000 }],
    recentOrders: [{ id: 'o-1', number: 1042, createdAt: '2026-10-15T15:00:00Z', buyerName: 'Ana Pérez',
      status: 'PAID', totalCents: 2_300_000 }],
    totals: { publishedProducts: 286 },
    ...overrides,
  };
}

/** Money as the page reads it: Testing Library turns the non-breaking space into a plain one. */
function money(cents: number) {
  return plain(formatMoney(cents));
}

/** Any text with a non-breaking space, as the page reads it. */
function plain(text: string) {
  return text.replace(/\u00a0/g, ' ');
}

function section(title: string) {
  return within(screen.getByRole('region', { name: title }));
}

describe('the back-office summary (UC-33, T-E8-13)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given an administrator, when /admin opens, then it shows the summary, not the product list', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });

    renderRoute('/admin');

    expect(await screen.findByRole('heading', { level: 1, name: messages.title })).toBeInTheDocument();
    expect(await screen.findByRole('region', { name: messages.todo.title })).toBeInTheDocument();
  });

  it('given work waiting, when the summary shows, then each count links to its list already filtered and a 0 does not link', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.todo.title });

    const todo = section(messages.todo.title);
    expect(todo.getByRole('link', { name: new RegExp(messages.todo.paidOrders) }))
      .toHaveAttribute('href', '/admin/pedidos?estado=PAID');
    expect(todo.getByRole('link', { name: new RegExp(messages.todo.receivedWithdrawals) }))
      .toHaveAttribute('href', '/admin/arrepentimientos?estado=RECEIVED');
    expect(todo.getByRole('link', { name: new RegExp(messages.todo.pendingReviews) }))
      .toHaveAttribute('href', '/admin/resenas');
    expect(todo.queryByRole('link', { name: new RegExp(messages.todo.preparingOrders) })).not.toBeInTheDocument();
    expect(todo.getByText(messages.todo.preparingOrders).closest('li')).toHaveTextContent('0');
  });

  it('given sales against the periods before, when the summary shows, then each period has its amount, orders and variation, "—" without sales before', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.sales.title });

    const sales = section(messages.sales.title);
    expect(sales.getByText(money(15_000_000))).toBeInTheDocument();
    expect(sales.getByText(plain(messages.sales.variation(50)))).toBeInTheDocument();
    expect(sales.getByText(plain(messages.sales.variation(-12)))).toBeInTheDocument();
    // The dash is for the eyes; screen readers hear why there is none.
    expect(sales.getByText(messages.sales.noVariation)).toHaveClass('visually-hidden');
    expect(sales.getByText(money(10_000_000))).toBeInTheDocument();
  });

  it('given the chart by days, when the months are chosen, then it asks for them and shows 12 rows for screen readers', async () => {
    const months = Array.from({ length: 12 }, (_, index) => ({
      period: `2026-${String(index + 1).padStart(2, '0')}`, amountCents: 0, orders: 0 }));
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: (options: { params?: { query?: Record<string, unknown> } }) =>
        answer(200, options.params?.query?.chart === 'MONTHS'
          ? dashboard({ chart: { kind: 'MONTHS', points: months } })
          : dashboard()),
    });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.chart.title });
    const chart = section(messages.chart.title);
    expect(chart.getByRole('table', { name: messages.chart.tableCaption('DAYS') })).toBeInTheDocument();
    expect(chart.getAllByRole('row')).toHaveLength(31);

    await userEvent.click(chart.getByRole('button', { name: messages.chart.months }));

    expect(await chart.findByRole('table', { name: messages.chart.tableCaption('MONTHS') })).toBeInTheDocument();
    expect(chart.getAllByRole('row')).toHaveLength(13);
    expect(chart.getByRole('button', { name: messages.chart.months })).toHaveAttribute('aria-pressed', 'true');
    expect(api.callsTo(DASHBOARD).map((call) => call.options.params?.query?.chart)).toEqual(['DAYS', 'MONTHS']);
  });

  it('given 12 variants at or under the threshold, when the summary shows, then 10 show at most, sold-out first, and the link reads 12', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.lowStock.title });

    const lowStock = section(messages.lowStock.title);
    expect(lowStock.getAllByRole('listitem')[0]).toHaveTextContent(messages.lowStock.soldOut);
    expect(lowStock.getByRole('link', { name: messages.lowStock.seeAll(12) })).toHaveAttribute('href', '/admin/productos');
  });

  it('given no variant at or under the threshold, when the summary shows, then low stock says so', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard({ lowStock: { threshold: 3, count: 0, items: [] } })) });
    renderRoute('/admin');

    expect(await screen.findByText(messages.lowStock.none)).toBeInTheDocument();
  });

  it('given recent orders and best sellers, when the summary shows, then each order links to its detail', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.recentOrders.title });

    expect(section(messages.recentOrders.title).getByRole('link', { name: '#1042' }))
      .toHaveAttribute('href', '/admin/pedidos/o-1');
    expect(section(messages.bestSellers.title).getByText('Pijama Luna')).toBeInTheDocument();
    expect(section(messages.totals.title).getByText('286')).toBeInTheDocument();
  });

  it('given the summary, when "Actualizar" is pressed, then it reads the figures again', async () => {
    const api = stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.todo.title });

    await userEvent.click(screen.getByRole('button', { name: messages.refresh }));

    await vi.waitFor(() => {
      expect(api.callsTo(DASHBOARD)).toHaveLength(2);
    });
  });

  it('given the summary cannot be read, when it opens, then it says so and offers to retry', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }), [DASHBOARD]: answer(500) });
    renderRoute('/admin');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.loadError);
    expect(screen.getByRole('button', { name: esAdmin.retry })).toBeInTheDocument();
  });
});
