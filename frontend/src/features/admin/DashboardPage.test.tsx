import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { components } from '../../api/schema.d.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { storeDay } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

type Body = components['schemas']['DashboardResponse'];

const messages = esAdmin.dashboard;
const DASHBOARD = 'GET /api/admin/dashboard';
const COUNTS = 'GET /api/admin/dashboard/counts';
const ACTIVITY = 'GET /api/admin/activity';
const NO_ACTIVITY = answer(200, { items: [], hasMore: false });

/** Every test reads the recent activity too (its own read); empty unless the test says otherwise. */
function stub(routes: Parameters<typeof stubApi>[0]) {
  return stubApi({ [ACTIVITY]: NO_ACTIVITY, ...routes });
}

type Query = { params?: { query?: Record<string, unknown> } };

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
    sales: { period: 'MONTH', from: '2026-10-01', to: '2026-10-15', amountCents: 30_000_000, orders: 3,
      variation: -12, averageTicketCents: 10_000_000 },
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
    totals: { publishedProducts: 286, customers: 1240, newCustomersThisMonth: 37 },
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
    vi.useRealTimers();
    localStorage.clear();
  });

  it('given an administrator, when /admin opens, then it shows the summary, not the product list', async () => {
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });

    renderRoute('/admin');

    expect(await screen.findByRole('heading', { level: 1, name: messages.title })).toBeInTheDocument();
    expect(await screen.findByRole('region', { name: messages.todo.title })).toBeInTheDocument();
  });

  it('given work waiting, when the summary shows, then each count links to its list already filtered and a 0 does not link', async () => {
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
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

  it('given a bounced email and 2 PAID orders, when the summary shows, then the alert is under "Urgente" and the paid orders under "Operativo"', async () => {
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard({ todo: { ...dashboard().todo, emailAlerts: 1 } })) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.todo.title });

    const todo = section(messages.todo.title);
    const urgent = within(todo.getByRole('list', { name: messages.todo.levels.urgent }));
    const operational = within(todo.getByRole('list', { name: messages.todo.levels.operational }));
    expect(urgent.getByRole('link', { name: new RegExp(messages.todo.emailAlerts) })).toBeInTheDocument();
    expect(operational.getByRole('link', { name: new RegExp(messages.todo.paidOrders) })).toBeInTheDocument();
    expect(within(todo.getByRole('list', { name: messages.todo.levels.inventory })).getByText(messages.todo.soldOutVariants))
      .toBeInTheDocument();
    expect(within(todo.getByRole('list', { name: messages.todo.levels.inventory }))
      .getByRole('link', { name: new RegExp(messages.todo.soldOutVariants) }))
      .toHaveAttribute('href', '/admin/stock?filtro=OUT');
  });

  it('given the summary read, when it shows, then it says when it was read', async () => {
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');

    expect(await screen.findByText(/^Actualizado a las \d{2}:\d{2}$/)).toBeInTheDocument();
  });

  it('given this month by default, when "Hoy" is chosen, then it asks for today and shows its figures, "—" without sales before', async () => {
    const api = stub({
      'GET /api/admin/auth/me': signedIn,
      [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: (options: Query) =>
        answer(200, options.params?.query?.period === 'TODAY'
          ? dashboard({ sales: { period: 'TODAY', from: '2026-10-15', to: '2026-10-15', amountCents: 15_000_000,
            orders: 1, variation: null, averageTicketCents: 15_000_000 } })
          : dashboard()),
    });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.sales.title });
    const sales = section(messages.sales.title);
    expect(sales.getByRole('radio', { name: messages.sales.periods.MONTH })).toBeChecked();
    expect(sales.getByText(money(30_000_000))).toBeInTheDocument();
    expect(sales.getByText(plain(messages.sales.variation(-12)))).toBeInTheDocument();
    expect(sales.getByText(money(10_000_000))).toBeInTheDocument();

    await userEvent.click(sales.getByRole('radio', { name: messages.sales.periods.TODAY }));

    expect(await sales.findByText(messages.sales.versus.TODAY, { exact: false })).toBeInTheDocument();
    expect(sales.getAllByText(money(15_000_000))).toHaveLength(2);
    // The dash is for the eyes; screen readers hear why there is none.
    expect(sales.getByText(messages.sales.noVariation)).toHaveClass('visually-hidden');
    expect(api.callsTo(DASHBOARD).map((call) => call.options.params?.query?.period)).toEqual(['MONTH', 'TODAY']);
  });

  it('given "Fechas", when valid dates are chosen, then it asks for them; when "Desde" is after "Hasta", it says so and does not ask', async () => {
    const today = storeDay(new Date());
    const first = `${today.slice(0, 8)}01`;
    const api = stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.sales.title });
    const sales = section(messages.sales.title);

    await userEvent.click(sales.getByRole('radio', { name: messages.sales.periods.CUSTOM }));

    await vi.waitFor(() => {
      expect(api.callsTo(DASHBOARD).at(-1)?.options.params?.query).toMatchObject({ period: 'CUSTOM', from: first, to: today });
    });
    const calls = api.callsTo(DASHBOARD).length;

    fireEvent.change(sales.getByLabelText(messages.sales.from), { target: { value: '2099-01-02' } });

    expect(sales.getByText(messages.sales.invalid.order)).toBeInTheDocument();
    expect(sales.getByLabelText(messages.sales.from)).toHaveAttribute('aria-invalid', 'true');
    expect(api.callsTo(DASHBOARD)).toHaveLength(calls);
  });

  it('given "Actualizar cada 5 minutos" on, when 5 minutes pass, then it reads the figures again, and the switch is remembered', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const api = stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    const view = renderRoute('/admin');
    await screen.findByRole('region', { name: messages.sales.title });
    const toggle = screen.getByRole('switch', { name: messages.autoRefresh });
    expect(toggle).not.toBeChecked();

    fireEvent.click(toggle);
    await vi.advanceTimersByTimeAsync(5 * 60 * 1000);

    await vi.waitFor(() => {
      expect(api.callsTo(DASHBOARD)).toHaveLength(2);
    });
    expect(api.callsTo(ACTIVITY)).toHaveLength(2);
    view.unmount();
    renderRoute('/admin');
    expect(await screen.findByRole('switch', { name: messages.autoRefresh })).toBeChecked();
  });

  it('given 12 variants with low stock under a threshold of 3, when the summary shows, then "Inventario" counts them', async () => {
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.todo.title });

    const inventory = within(section(messages.todo.title).getByRole('list', { name: messages.todo.levels.inventory }));
    expect(inventory.getByRole('link', { name: `12 ${messages.todo.lowStock(3)}` }))
      .toHaveAttribute('href', '/admin/stock?filtro=LOW');
  });

  it('given 7 activity entries, when the summary shows, then "Actividad reciente" lists the 5 newest', async () => {
    const entries = Array.from({ length: 7 }, (_, index) => ({
      id: `a-${String(index)}`,
      occurredAt: '2026-10-15T15:00:00Z',
      area: 'CATALOG',
      action: 'PRODUCT_HIDDEN',
      subject: { type: 'PRODUCT', id: 'p-1', label: `Pijama ${String(index)}` },
      changes: [],
    }));
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()), [ACTIVITY]: answer(200, { items: entries, hasMore: false }) });
    renderRoute('/admin');

    const activity = await screen.findByRole('region', { name: messages.activity.title });
    await within(activity).findByText('Pijama 0');
    expect(within(activity).getAllByRole('listitem')).toHaveLength(5);
    expect(within(activity).getByRole('link', { name: messages.activity.seeAll })).toHaveAttribute('href', '/admin/actividad');
  });

  it('given the activity cannot be read, when the summary shows, then only its block says so', async () => {
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()), [ACTIVITY]: answer(500) });
    renderRoute('/admin');

    const activity = await screen.findByRole('region', { name: messages.activity.title });
    expect(await within(activity).findByText(messages.activity.loadError)).toBeInTheDocument();
    expect(screen.getByRole('region', { name: messages.sales.title })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('given the chart by days, when the months are chosen, then it asks for them and shows 12 rows for screen readers', async () => {
    const months = Array.from({ length: 12 }, (_, index) => ({
      period: `2026-${String(index + 1).padStart(2, '0')}`, amountCents: 0, orders: 0 }));
    const api = stub({
      'GET /api/admin/auth/me': signedIn,
      [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: (options: Query) =>
        answer(200, options.params?.query?.chart === 'MONTHS'
          ? dashboard({ chart: { kind: 'MONTHS', points: months } })
          : dashboard()),
    });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.chart.title });
    const chart = section(messages.chart.title);
    expect(chart.getByRole('table', { name: messages.chart.tableCaption('DAYS') })).toBeInTheDocument();
    expect(chart.getAllByRole('row')).toHaveLength(31);

    await userEvent.click(chart.getByRole('radio', { name: messages.chart.months }));

    expect(await chart.findByRole('table', { name: messages.chart.tableCaption('MONTHS') })).toBeInTheDocument();
    expect(chart.getAllByRole('row')).toHaveLength(13);
    expect(chart.getByRole('radio', { name: messages.chart.months })).toBeChecked();
    // 12 months without a sale: an empty state instead of the bars (D-E8-12)
    expect(chart.getByText(messages.chart.empty)).toBeInTheDocument();
    expect(api.callsTo(DASHBOARD).map((call) => call.options.params?.query?.chart)).toEqual(['DAYS', 'MONTHS']);
  });

  it('given 12 variants at or under the threshold, when the summary shows, then 10 show at most, sold-out first, and the link reads 12', async () => {
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.lowStock.title });

    const lowStock = section(messages.lowStock.title);
    expect(lowStock.getAllByRole('listitem')[0]).toHaveTextContent(messages.lowStock.soldOut);
    expect(lowStock.getByRole('link', { name: messages.lowStock.seeAll })).toHaveAttribute('href', '/admin/stock?filtro=LOW');
  });

  it('given no variant at or under the threshold, when the summary shows, then low stock says so', async () => {
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard({ lowStock: { threshold: 3, count: 0, items: [] } })) });
    renderRoute('/admin');

    expect(await screen.findByText(messages.lowStock.none)).toBeInTheDocument();
  });

  it('given recent orders and best sellers, when the summary shows, then each order links to its detail', async () => {
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.recentOrders.title });

    expect(section(messages.recentOrders.title).getByRole('link', { name: '#1042' }))
      .toHaveAttribute('href', '/admin/pedidos/o-1');
    expect(section(messages.bestSellers.title).getByText('Pijama Luna')).toBeInTheDocument();
    expect(section(messages.totals.title).getByText('286')).toBeInTheDocument();
    // UC-33 (T-E8-14): the registered customers, with this month's new ones.
    expect(section(messages.totals.title).getByText('1.240')).toBeInTheDocument();
    expect(section(messages.totals.title).getByText(messages.totals.customers)).toBeInTheDocument();
    expect(section(messages.totals.title).getByText(messages.totals.newCustomers(37))).toBeInTheDocument();
  });

  it('given the summary, when "Actualizar" is pressed, then it reads the figures again', async () => {
    const api = stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }),
      [DASHBOARD]: answer(200, dashboard()) });
    renderRoute('/admin');
    await screen.findByRole('region', { name: messages.todo.title });

    await userEvent.click(screen.getByRole('button', { name: messages.refresh }));

    await vi.waitFor(() => {
      expect(api.callsTo(DASHBOARD)).toHaveLength(2);
    });
  });

  it('given the summary cannot be read, when it opens, then it says so and offers to retry', async () => {
    stub({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(200, { paidOrders: 2 }), [DASHBOARD]: answer(500) });
    renderRoute('/admin');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.loadError);
    expect(screen.getByRole('button', { name: esAdmin.retry })).toBeInTheDocument();
  });
});
