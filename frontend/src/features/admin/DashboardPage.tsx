import type { VariantProps } from 'class-variance-authority';
import {
  CircleAlert,
  ClipboardList,
  PackageX,
  Plus,
  RefreshCw,
  ShoppingBag,
  Ticket,
  TrendingDown,
  TrendingUp,
  type LucideIcon,
} from 'lucide-react';
import { useId, useState, type CSSProperties } from 'react';
import { Link } from 'react-router';
import { Badge, type badgeVariants } from '../../components/ui/Badge.tsx';
import { Button } from '../../components/ui/Button.tsx';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/Card.tsx';
import { cn } from '../../components/ui/cn.ts';
import { SegmentedControl } from '../../components/ui/SegmentedControl.tsx';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDateTime, formatTime } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { useDashboard, type Chart, type Dashboard, type PeriodSales } from './dashboardApi.ts';
import type { OrderStatus } from './ordersApi.ts';
import { SalesChart } from './SalesChart.tsx';

const messages = esAdmin.dashboard;

/**
 * D-E8-12 motion: the cards rise in one after another when the summary first shows, once; a refresh keeps them
 * mounted, so it does not move the screen. Nothing moves under reduced motion (motion-safe).
 */
function rise(order: number): { className: string; style: CSSProperties } {
  return { className: 'motion-safe:animate-rise', style: { animationDelay: `${String(order * 50)}ms` } };
}

type Level = keyof typeof messages.todo.levels;

interface TodoEntry {
  key: keyof Dashboard['todo'];
  /** Where the count leads, already filtered. */
  to: string;
}

/**
 * "Para hacer hoy" in its three levels (D-E8-12), most urgent first. The email alerts lead to the order list (it has
 * no filter for them yet), and the sold-out variants to the products until the Stock screen exists (T-E8-09).
 */
const LEVELS: readonly { level: Level; icon: LucideIcon; entries: readonly TodoEntry[] }[] = [
  {
    level: 'urgent',
    icon: CircleAlert,
    entries: [
      { key: 'emailAlerts', to: '/admin/pedidos' },
      { key: 'receivedWithdrawals', to: '/admin/arrepentimientos?estado=RECEIVED' },
    ],
  },
  {
    level: 'operational',
    icon: ClipboardList,
    entries: [
      { key: 'paidOrders', to: '/admin/pedidos?estado=PAID' },
      { key: 'preparingOrders', to: '/admin/pedidos?estado=PREPARING' },
      { key: 'pendingReviews', to: '/admin/resenas' },
    ],
  },
  { level: 'inventory', icon: PackageX, entries: [{ key: 'soldOutVariants', to: '/admin/productos' }] },
];

/** Each level by tone and weight, not by color alone: its titled group, and a count above 0 tinted in its tone. */
const LEVEL_TONE: Record<Level, { title: string; row: string }> = {
  urgent: { title: 'text-destructive-strong', row: 'bg-destructive-soft text-destructive-strong' },
  operational: { title: 'text-primary-text', row: 'bg-secondary text-primary-text' },
  inventory: { title: 'text-warning', row: 'bg-warning-soft text-warning' },
};

const ROW = 'flex min-h-11 w-full items-center gap-3 rounded-md px-3 py-2 text-sm no-underline';

function Todo({ todo, order }: { todo: Dashboard['todo']; order: number }) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card aria-labelledby={titleId} className={cn('min-[860px]:col-span-3', className)} style={style}>
      <CardHeader>
        <CardTitle id={titleId}>{messages.todo.title}</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4 min-[860px]:grid-cols-3">
        {LEVELS.map(({ level, icon: Icon, entries }) => (
          <TodoLevel key={level} level={level} icon={Icon} entries={entries} todo={todo} />
        ))}
      </CardContent>
    </Card>
  );
}

function TodoLevel({ level, icon: Icon, entries, todo }: {
  level: Level;
  icon: LucideIcon;
  entries: readonly TodoEntry[];
  todo: Dashboard['todo'];
}) {
  const titleId = useId();
  const tone = LEVEL_TONE[level];
  return (
    <div className="flex flex-col gap-1.5">
      <h3 id={titleId} className={cn('m-0 flex items-center gap-1.5 text-xs font-bold tracking-wider uppercase', tone.title)}>
        <Icon aria-hidden="true" className="size-4" />
        {messages.todo.levels[level]}
      </h3>
      <ul aria-labelledby={titleId} className="m-0 flex list-none flex-col gap-1 p-0">
        {entries.map(({ key, to }) => {
          const count = todo[key];
          const content = (
            <>
              <span className="min-w-7 text-xl font-bold tabular-nums">{count.toLocaleString('es-AR')}</span>
              <span className="font-medium">{messages.todo[key]}</span>
            </>
          );
          // A count of 0 still shows, without a link (UC-33), and quiet.
          return (
            <li key={key}>
              {count === 0 ? (
                <span className={cn(ROW, 'text-muted-foreground')}>{content}</span>
              ) : (
                <Link
                  to={to}
                  className={cn(ROW, tone.row, 'transition-[filter] hover:brightness-[0.97] motion-safe:active:scale-[0.99]')}
                >
                  {content}
                </Link>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function Variation({ variation, versus }: { variation: number | null; versus: string }) {
  if (variation === null) {
    return (
      <span className="text-muted-foreground">
        <span aria-hidden="true">{messages.sales.variation(null)}</span>
        <span className="visually-hidden">{messages.sales.noVariation}</span> {versus}
      </span>
    );
  }
  const Icon = variation > 0 ? TrendingUp : variation < 0 ? TrendingDown : null;
  const tone = variation > 0 ? 'text-success' : variation < 0 ? 'text-destructive-strong' : 'text-muted-foreground';
  return (
    <span className="inline-flex items-center gap-1">
      {Icon !== null && <Icon aria-hidden="true" className={cn('size-3.5', tone)} />}
      <span className={cn('font-semibold whitespace-nowrap', tone)}>{messages.sales.variation(variation)}</span>
      <span className="text-muted-foreground">{versus}</span>
    </span>
  );
}

/** The figure of a block, the largest thing in it (D-E8-12); a new value fades into place. */
function Figure({ value, className }: { value: string; className?: string }) {
  return (
    <p key={value} className={cn('m-0 text-[26px] leading-tight font-bold tabular-nums motion-safe:animate-fade', className)}>
      {value}
    </p>
  );
}

function Period({ label, versus, sales }: { label: string; versus: string; sales: PeriodSales }) {
  return (
    <div className="flex flex-col gap-1 rounded-lg border border-border p-4">
      <p className="m-0 text-[13px] font-semibold text-muted-foreground">{label}</p>
      <Figure value={formatMoney(sales.amountCents)} />
      <p className="m-0 flex flex-wrap gap-x-1.5 text-[13px]">
        <span className="text-muted-foreground">{messages.sales.orders(sales.orders)} ·</span>
        <Variation variation={sales.variation} versus={versus} />
      </p>
    </div>
  );
}

function Sales({ sales, order }: { sales: Dashboard['sales']; order: number }) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card aria-labelledby={titleId} className={cn('min-[860px]:col-span-3', className)} style={style}>
      <CardHeader>
        <CardTitle id={titleId}>{messages.sales.title}</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-3 min-[560px]:grid-cols-2 min-[1100px]:grid-cols-4">
        <Period label={messages.sales.today} versus={messages.sales.versus.today} sales={sales.today} />
        <Period label={messages.sales.lastWeek} versus={messages.sales.versus.lastWeek} sales={sales.lastWeek} />
        <Period label={messages.sales.month} versus={messages.sales.versus.month} sales={sales.month} />
        {/* The highlighted figure: the month's average ticket, tinted as the store's accent */}
        <div className="flex flex-col gap-1 rounded-lg bg-accent p-4 text-accent-foreground">
          <p className="m-0 text-[13px] font-semibold">{messages.sales.averageTicket}</p>
          <Figure
            value={sales.averageTicketCents === null ? messages.sales.none : formatMoney(sales.averageTicketCents)}
          />
        </div>
      </CardContent>
    </Card>
  );
}

interface ChartBlockProps {
  chart: Dashboard['chart'];
  /** The chart asked for: while it loads, the one on screen is still the other. */
  requested: Chart;
  loading: boolean;
  onKind: (kind: Chart) => void;
  order: number;
}

function ChartBlock({ chart, requested, loading, onKind, order }: ChartBlockProps) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card aria-labelledby={titleId} aria-busy={loading} className={cn('min-[860px]:col-span-2', className)} style={style}>
      <CardHeader>
        <div className="flex flex-col gap-0.5">
          <CardTitle id={titleId}>{messages.chart.title}</CardTitle>
          <CardDescription>{messages.chart.tableCaption(requested)}</CardDescription>
        </div>
        <SegmentedControl
          label={messages.chart.kindLabel}
          options={[
            { value: 'DAYS', label: messages.chart.days },
            { value: 'MONTHS', label: messages.chart.months },
          ]}
          value={requested}
          onValueChange={onKind}
        />
      </CardHeader>
      <CardContent className={cn('transition-opacity', loading && 'opacity-60')}>
        <SalesChart kind={chart.kind} points={chart.points} />
      </CardContent>
    </Card>
  );
}

function QuickActions({ order }: { order: number }) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card aria-labelledby={titleId} className={className} style={style}>
      <CardHeader>
        <CardTitle id={titleId}>{messages.quick.title}</CardTitle>
      </CardHeader>
      {/* The screen's one primary action (D-E8-12); the others are secondary */}
      <CardContent className="gap-2">
        <Button asChild variant="primary">
          <Link to="/admin/productos/nuevo">
            <Plus aria-hidden="true" />
            {messages.quick.newProduct}
          </Link>
        </Button>
        <Button asChild variant="secondary">
          <Link to="/admin/cupones">
            <Ticket aria-hidden="true" />
            {messages.quick.coupons}
          </Link>
        </Button>
        <Button asChild variant="secondary">
          <Link to="/admin/pedidos">
            <ShoppingBag aria-hidden="true" />
            {messages.quick.orders}
          </Link>
        </Button>
      </CardContent>
    </Card>
  );
}

const ROWS = 'm-0 flex list-none flex-col p-0 [&>li]:flex [&>li]:min-h-11 [&>li]:items-center [&>li]:justify-between [&>li]:gap-3 [&>li]:border-b [&>li]:border-border [&>li]:py-2 [&>li:last-child]:border-b-0';

/** A status's tone: what waits on the store stands out, what is done or closed is quiet. */
const STATUS_TONE: Record<OrderStatus, VariantProps<typeof badgeVariants>['tone']> = {
  AWAITING_PAYMENT: 'neutral',
  PAID: 'accent',
  PREPARING: 'warning',
  SHIPPED: 'info',
  DELIVERED: 'success',
  CANCELLED: 'danger',
  EXPIRED: 'neutral',
};

function RecentOrders({ orders, order }: { orders: Dashboard['recentOrders']; order: number }) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card aria-labelledby={titleId} className={cn('min-[860px]:col-span-2', className)} style={style}>
      <CardHeader>
        <CardTitle id={titleId}>{messages.recentOrders.title}</CardTitle>
        {orders.length > 0 && (
          <Button asChild variant="link" size="sm">
            <Link to="/admin/pedidos">{messages.recentOrders.seeAll}</Link>
          </Button>
        )}
      </CardHeader>
      <CardContent>
        {orders.length === 0 ? (
          <EmptyText>{messages.recentOrders.none}</EmptyText>
        ) : (
          <ul className={ROWS}>
            {orders.map((recent) => (
              <li key={recent.id}>
                <span className="flex min-w-0 flex-col">
                  <Link
                    to={`/admin/pedidos/${recent.id}`}
                    className="self-start font-semibold text-primary-text no-underline hover:underline"
                  >
                    #{recent.number}
                  </Link>
                  <span className="truncate text-[13px] text-muted-foreground">
                    {`${recent.buyerName} · ${formatDateTime(recent.createdAt)}`}
                  </span>
                </span>
                <span className="flex shrink-0 flex-col items-end gap-1">
                  <span className="text-sm font-semibold tabular-nums">{formatMoney(recent.totalCents)}</span>
                  <Badge tone={STATUS_TONE[recent.status]}>{es.tracking.statuses[recent.status]}</Badge>
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function EmptyText({ children }: { children: string }) {
  return <p className="m-0 rounded-lg border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">{children}</p>;
}

function BestSellers({ bestSellers, order }: { bestSellers: Dashboard['bestSellers']; order: number }) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card aria-labelledby={titleId} className={className} style={style}>
      <CardHeader>
        <div className="flex flex-col gap-0.5">
          <CardTitle id={titleId}>{messages.bestSellers.title}</CardTitle>
          <CardDescription>{messages.bestSellers.subtitle}</CardDescription>
        </div>
      </CardHeader>
      <CardContent>
        {bestSellers.length === 0 ? (
          <EmptyText>{messages.bestSellers.none}</EmptyText>
        ) : (
          <ol className={ROWS}>
            {bestSellers.map((sold, index) => (
              <li key={sold.productSlug}>
                <span className="flex min-w-0 items-center gap-3">
                  <span
                    aria-hidden="true"
                    className="grid size-6 shrink-0 place-items-center rounded-full bg-muted text-xs font-bold text-muted-foreground"
                  >
                    {index + 1}
                  </span>
                  <span className="flex min-w-0 flex-col">
                    <span className="text-sm font-semibold">{sold.productName}</span>
                    <span className="text-[13px] text-muted-foreground">
                      {messages.bestSellers.units(sold.units)} · {formatMoney(sold.amountCents)}
                    </span>
                  </span>
                </span>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  );
}

function LowStock({ lowStock, order }: { lowStock: Dashboard['lowStock']; order: number }) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card aria-labelledby={titleId} className={cn('min-[860px]:col-span-2', className)} style={style}>
      <CardHeader>
        <div className="flex flex-col gap-0.5">
          <CardTitle id={titleId}>{messages.lowStock.title}</CardTitle>
          <CardDescription>{messages.lowStock.threshold(lowStock.threshold)}</CardDescription>
        </div>
        {lowStock.items.length > 0 && (
          <Button asChild variant="link" size="sm">
            <Link to="/admin/productos">{messages.lowStock.seeAll(lowStock.count)}</Link>
          </Button>
        )}
      </CardHeader>
      <CardContent>
        {lowStock.items.length === 0 ? (
          <EmptyText>{messages.lowStock.none}</EmptyText>
        ) : (
          <ul className={ROWS}>
            {lowStock.items.map((item) => (
              <li key={item.variantId}>
                <span className="flex min-w-0 flex-col">
                  <span className="truncate text-sm font-semibold">{item.productName}</span>
                  <span className="text-[13px] text-muted-foreground">{`${item.size} · ${item.color}`}</span>
                </span>
                <Badge tone={item.available === 0 ? 'danger' : 'warning'}>
                  {item.available === 0 ? messages.lowStock.soldOut : messages.lowStock.available(item.available)}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function Totals({ totals, order }: { totals: Dashboard['totals']; order: number }) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card aria-labelledby={titleId} className={className} style={style}>
      <CardHeader>
        <CardTitle id={titleId}>{messages.totals.title}</CardTitle>
      </CardHeader>
      <CardContent className="gap-1">
        <Figure value={totals.publishedProducts.toLocaleString('es-AR')} />
        <p className="m-0 text-[13px] text-muted-foreground">{messages.totals.publishedProducts}</p>
      </CardContent>
    </Card>
  );
}

/**
 * UC-33 at /admin: the summary the back office opens on, in the look of D-E8-12. The figures are read when the page
 * opens and again with "Actualizar"; nothing refreshes by itself. One column at 360 px, three from 860 px (RNF-26).
 */
export function DashboardPage() {
  const [chart, setChart] = useState<Chart>('DAYS');
  const dashboard = useDashboard(chart);

  return (
    <>
      <title>{`${messages.title} | ${esAdmin.title}`}</title>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h1>{messages.title}</h1>
          <p className="m-0 text-sm text-muted-foreground">{messages.intro}</p>
        </div>
        <div className="flex items-center gap-3">
          {dashboard.data !== undefined && (
            <p className="m-0 text-[13px] text-muted-foreground tabular-nums">
              {messages.updatedAt(formatTime(new Date(dashboard.dataUpdatedAt)))}
            </p>
          )}
          {/* Focusable while it reads (aria-disabled), so a keyboard does not lose its place. */}
          <Button
            variant="secondary"
            aria-disabled={dashboard.isFetching}
            onClick={() => {
              if (!dashboard.isFetching) {
                void dashboard.refetch();
              }
            }}
          >
            <RefreshCw aria-hidden="true" className={cn(dashboard.isFetching && 'motion-safe:animate-spin')} />
            {messages.refresh}
          </Button>
        </div>
      </div>
      {/* Always mounted, so screen readers hear each change: loading, refreshing, and back to quiet. */}
      <p className="visually-hidden" role="status">
        {dashboard.isPending ? messages.loading : dashboard.isFetching ? messages.refreshing : ''}
      </p>
      {dashboard.isPending && (
        <p className="m-0 text-sm text-muted-foreground" aria-hidden="true">
          {messages.loading}
        </p>
      )}
      {dashboard.isError && (
        <div role="alert" className="flex flex-col items-start gap-3">
          <p className="m-0">{messages.loadError}</p>
          <Button variant="primary" onClick={() => void dashboard.refetch()}>
            {esAdmin.retry}
          </Button>
        </div>
      )}
      {/* Words wrap whole here: the back office's overflow-wrap: anywhere would split figures and labels in narrow cards */}
      {dashboard.data !== undefined && (
        <div className="grid gap-4 wrap-break-word min-[860px]:grid-cols-3">
          <Todo todo={dashboard.data.todo} order={0} />
          <Sales sales={dashboard.data.sales} order={1} />
          <ChartBlock
            chart={dashboard.data.chart}
            requested={chart}
            loading={dashboard.isPlaceholderData}
            onKind={setChart}
            order={2}
          />
          <QuickActions order={3} />
          <RecentOrders orders={dashboard.data.recentOrders} order={4} />
          <BestSellers bestSellers={dashboard.data.bestSellers} order={5} />
          <LowStock lowStock={dashboard.data.lowStock} order={6} />
          <Totals totals={dashboard.data.totals} order={7} />
        </div>
      )}
    </>
  );
}
