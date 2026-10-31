import { useId, useState } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDateTime } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { useDashboard, type Chart, type Dashboard, type PeriodSales } from './dashboardApi.ts';
import { SalesChart } from './SalesChart.tsx';

const messages = esAdmin.dashboard;

interface TodoEntry {
  key: keyof Dashboard['todo'];
  /** Where the count leads, already filtered. */
  to: string;
}

/**
 * "Para hacer hoy" in order of urgency. The email alerts lead to the order list (it has no filter for them yet), and
 * the sold-out variants to the products until the Stock screen exists (T-E8-09).
 */
const TODO: readonly TodoEntry[] = [
  { key: 'paidOrders', to: '/admin/pedidos?estado=PAID' },
  { key: 'preparingOrders', to: '/admin/pedidos?estado=PREPARING' },
  { key: 'receivedWithdrawals', to: '/admin/arrepentimientos?estado=RECEIVED' },
  { key: 'emailAlerts', to: '/admin/pedidos' },
  { key: 'pendingReviews', to: '/admin/resenas' },
  { key: 'soldOutVariants', to: '/admin/productos' },
];

function Todo({ todo }: { todo: Dashboard['todo'] }) {
  const titleId = useId();
  return (
    <section className="dashboard-card dashboard-todo" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.todo.title}</h2>
      <ul>
        {TODO.map(({ key, to }) => {
          const count = todo[key];
          const content = (
            <>
              <span className="dashboard-count">{count.toLocaleString('es-AR')}</span>
              <span>{messages.todo[key]}</span>
            </>
          );
          // A count of 0 still shows, without a link (UC-33).
          return (
            <li key={key} className={count === 0 ? 'done' : undefined}>
              {count === 0 ? <span className="dashboard-todo-row">{content}</span> : (
                <Link className="dashboard-todo-row" to={to}>
                  {content}
                </Link>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function Period({ label, versus, sales }: { label: string; versus: string; sales: PeriodSales }) {
  const trend = sales.variation === null ? 'none' : sales.variation > 0 ? 'up' : sales.variation < 0 ? 'down' : 'flat';
  return (
    <div className="dashboard-period">
      <p className="dashboard-period-label">{label}</p>
      <p className="dashboard-amount">{formatMoney(sales.amountCents)}</p>
      <p className="dashboard-period-meta">
        {messages.sales.orders(sales.orders)}
        {' · '}
        <span className={`dashboard-variation ${trend}`}>
          {sales.variation === null ? (
            <>
              <span aria-hidden="true">{messages.sales.variation(null)}</span>
              <span className="visually-hidden">{messages.sales.noVariation}</span>
            </>
          ) : (
            messages.sales.variation(sales.variation)
          )}
        </span>{' '}
        {versus}
      </p>
    </div>
  );
}

function Sales({ sales }: { sales: Dashboard['sales'] }) {
  const titleId = useId();
  return (
    <section className="dashboard-card dashboard-sales" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.sales.title}</h2>
      <div className="dashboard-periods">
        <Period label={messages.sales.today} versus={messages.sales.versus.today} sales={sales.today} />
        <Period label={messages.sales.lastWeek} versus={messages.sales.versus.lastWeek} sales={sales.lastWeek} />
        <Period label={messages.sales.month} versus={messages.sales.versus.month} sales={sales.month} />
      </div>
      <p className="dashboard-ticket">
        {messages.sales.averageTicket}:{' '}
        <strong>
          {sales.averageTicketCents === null ? messages.sales.none : formatMoney(sales.averageTicketCents)}
        </strong>
      </p>
    </section>
  );
}

interface ChartBlockProps {
  chart: Dashboard['chart'];
  /** The chart asked for: while it loads, the one on screen is still the other. */
  requested: Chart;
  loading: boolean;
  onKind: (kind: Chart) => void;
}

function ChartBlock({ chart, requested, loading, onKind }: ChartBlockProps) {
  const titleId = useId();
  return (
    <section className="dashboard-card dashboard-chart" aria-labelledby={titleId} aria-busy={loading}>
      <div className="dashboard-card-head">
        <h2 id={titleId}>{messages.chart.title}</h2>
        <div className="dashboard-toggle" role="group" aria-label={messages.chart.kindLabel}>
          {(['DAYS', 'MONTHS'] as const).map((kind) => (
            <button
              key={kind}
              type="button"
              aria-pressed={requested === kind}
              onClick={() => {
                onKind(kind);
              }}
            >
              {kind === 'DAYS' ? messages.chart.days : messages.chart.months}
            </button>
          ))}
        </div>
      </div>
      <SalesChart kind={chart.kind} points={chart.points} />
    </section>
  );
}

function LowStock({ lowStock }: { lowStock: Dashboard['lowStock'] }) {
  const titleId = useId();
  return (
    <section className="dashboard-card" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.lowStock.title}</h2>
      <p className="dashboard-subtitle">{messages.lowStock.threshold(lowStock.threshold)}</p>
      {lowStock.items.length === 0 ? (
        <p>{messages.lowStock.none}</p>
      ) : (
        <>
          <ul className="dashboard-rows">
            {lowStock.items.map((item) => (
              <li key={item.variantId}>
                <span>
                  {item.productName}
                  <span className="dashboard-muted">{` · ${item.size} · ${item.color}`}</span>
                </span>
                <span className={item.available === 0 ? 'admin-badge danger' : 'admin-badge'}>
                  {item.available === 0 ? messages.lowStock.soldOut : messages.lowStock.available(item.available)}
                </span>
              </li>
            ))}
          </ul>
          <Link to="/admin/productos">{messages.lowStock.seeAll(lowStock.count)}</Link>
        </>
      )}
    </section>
  );
}

function BestSellers({ bestSellers }: { bestSellers: Dashboard['bestSellers'] }) {
  const titleId = useId();
  return (
    <section className="dashboard-card" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.bestSellers.title}</h2>
      <p className="dashboard-subtitle">{messages.bestSellers.subtitle}</p>
      {bestSellers.length === 0 ? (
        <p>{messages.bestSellers.none}</p>
      ) : (
        <ol className="dashboard-rows">
          {bestSellers.map((sold) => (
            <li key={sold.productSlug}>
              <span>{sold.productName}</span>
              <span className="dashboard-muted">
                {messages.bestSellers.units(sold.units)} · {formatMoney(sold.amountCents)}
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function RecentOrders({ orders }: { orders: Dashboard['recentOrders'] }) {
  const titleId = useId();
  return (
    <section className="dashboard-card" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.recentOrders.title}</h2>
      {orders.length === 0 ? (
        <p>{messages.recentOrders.none}</p>
      ) : (
        <>
          <ul className="dashboard-rows">
            {orders.map((order) => (
              <li key={order.id}>
                <span>
                  <Link to={`/admin/pedidos/${order.id}`}>#{order.number}</Link>
                  <span className="dashboard-muted">{` · ${order.buyerName} · ${formatDateTime(order.createdAt)}`}</span>
                </span>
                <span className="dashboard-order-end">
                  <span className="admin-badge">{es.tracking.statuses[order.status]}</span>
                  {formatMoney(order.totalCents)}
                </span>
              </li>
            ))}
          </ul>
          <Link to="/admin/pedidos">{messages.recentOrders.seeAll}</Link>
        </>
      )}
    </section>
  );
}

function Totals({ totals }: { totals: Dashboard['totals'] }) {
  const titleId = useId();
  return (
    <section className="dashboard-card" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.totals.title}</h2>
      <p className="dashboard-total">
        <span className="dashboard-count">{totals.publishedProducts.toLocaleString('es-AR')}</span>
        {messages.totals.publishedProducts}
      </p>
    </section>
  );
}

/**
 * UC-33 at /admin: the summary the back office opens on. The figures are read when the page opens and again with
 * "Actualizar"; nothing refreshes by itself. One column at 360 px, two from 860 px (RNF-26).
 */
export function DashboardPage() {
  const [chart, setChart] = useState<Chart>('DAYS');
  const dashboard = useDashboard(chart);

  return (
    <>
      <title>{`${messages.title} | ${esAdmin.title}`}</title>
      <div className="admin-title-row">
        <div>
          <h1>{messages.title}</h1>
          <p className="dashboard-subtitle">{messages.intro}</p>
        </div>
        {/* Focusable while it reads (aria-disabled), so a keyboard does not lose its place. */}
        <button
          type="button"
          className="btn btn-secondary"
          aria-disabled={dashboard.isFetching}
          onClick={() => {
            if (!dashboard.isFetching) {
              void dashboard.refetch();
            }
          }}
        >
          {messages.refresh}
        </button>
      </div>
      {/* Always mounted, so screen readers hear each change: loading, refreshing, and back to quiet. */}
      <p className="visually-hidden" role="status">
        {dashboard.isPending ? messages.loading : dashboard.isFetching ? messages.refreshing : ''}
      </p>
      {dashboard.isPending && (
        <p className="dashboard-subtitle" aria-hidden="true">
          {messages.loading}
        </p>
      )}
      {dashboard.isError && (
        <div role="alert">
          <p>{messages.loadError}</p>
          <button type="button" className="btn" onClick={() => void dashboard.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      )}
      {dashboard.data !== undefined && (
        <div className="dashboard-grid">
          <Todo todo={dashboard.data.todo} />
          <Sales sales={dashboard.data.sales} />
          <ChartBlock
            chart={dashboard.data.chart}
            requested={chart}
            loading={dashboard.isPlaceholderData}
            onKind={setChart}
          />
          <LowStock lowStock={dashboard.data.lowStock} />
          <BestSellers bestSellers={dashboard.data.bestSellers} />
          <RecentOrders orders={dashboard.data.recentOrders} />
          <Totals totals={dashboard.data.totals} />
        </div>
      )}
    </>
  );
}
