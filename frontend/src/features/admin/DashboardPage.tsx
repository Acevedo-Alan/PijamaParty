import type { VariantProps } from 'class-variance-authority';
import { useQuery } from '@tanstack/react-query';
import {
  CircleAlert,
  ClipboardList,
  History,
  PackageX,
  Plus,
  RefreshCw,
  ShoppingBag,
  Ticket,
  TrendingDown,
  TrendingUp,
  type LucideIcon,
} from 'lucide-react';
import { useId, useState } from 'react';
import { Link } from 'react-router';
import { Badge, type badgeVariants } from '../../components/ui/Badge.tsx';
import { Button } from '../../components/ui/Button.tsx';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/Card.tsx';
import { cn } from '../../components/ui/cn.ts';
import { EmptyState } from '../../components/ui/EmptyState.tsx';
import { SegmentedControl } from '../../components/ui/SegmentedControl.tsx';
import { Switch } from '../../components/ui/Switch.tsx';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDateTime, formatDayMonth, formatTime, storeDay } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { fetchActivity } from './activityApi.ts';
import { subjectLabel } from './activitySubjects.ts';
import { useAutoRefresh } from './autoRefresh.ts';
import {
  AUTO_REFRESH_MS,
  useDashboard,
  type Chart,
  type Dashboard,
  type PeriodChoice,
  type SalesPeriod,
} from './dashboardApi.ts';
import type { OrderStatus } from './ordersApi.ts';
import { rise } from './rise.ts';
import { SalesChart } from './SalesChart.tsx';

const messages = esAdmin.dashboard;

type Level = keyof typeof messages.todo.levels;

interface TodoEntry {
  id: string;
  /** Where the count leads, already filtered. */
  to: string;
  count: (dashboard: Dashboard) => number;
  label: (dashboard: Dashboard) => string;
}

/** An entry counted by "Para hacer hoy" itself. */
function todo(key: keyof Dashboard['todo'], to: string): TodoEntry {
  return { id: key, to, count: (dashboard) => dashboard.todo[key], label: () => messages.todo[key] };
}

/**
 * UC-33 (decided on 2026-11-04): the sold-out and low-stock variants lead to the Stock screen filtered the same way
 * (UC-07, T-E8-09). Its figures can differ from the summary's: the summary counts variants of published products only,
 * and its low stock includes the sold-out ones, which the Stock screen lists under OUT.
 */
const SOLD_OUT_VARIANTS = '/admin/stock?filtro=OUT';
const LOW_STOCK_VARIANTS = '/admin/stock?filtro=LOW';

/**
 * "Para hacer hoy" in its three levels (D-E8-12), most urgent first. The email alerts lead to the order list (it has
 * no filter for them yet); the sold-out and low-stock variants to the Stock screen. The low-stock count is the
 * low-stock block's (T-E8-20).
 */
const LEVELS: readonly { level: Level; icon: LucideIcon; entries: readonly TodoEntry[] }[] = [
  {
    level: 'urgent',
    icon: CircleAlert,
    entries: [
      todo('emailAlerts', '/admin/pedidos'),
      todo('receivedWithdrawals', '/admin/arrepentimientos?estado=RECEIVED'),
    ],
  },
  {
    level: 'operational',
    icon: ClipboardList,
    entries: [
      todo('paidOrders', '/admin/pedidos?estado=PAID'),
      todo('preparingOrders', '/admin/pedidos?estado=PREPARING'),
      todo('pendingReviews', '/admin/resenas'),
    ],
  },
  {
    level: 'inventory',
    icon: PackageX,
    entries: [
      todo('soldOutVariants', SOLD_OUT_VARIANTS),
      {
        id: 'lowStock',
        to: LOW_STOCK_VARIANTS,
        count: (dashboard) => dashboard.lowStock.count,
        label: (dashboard) => messages.todo.lowStock(dashboard.lowStock.threshold),
      },
    ],
  },
];

/** Each level by tone and weight, not by color alone: its titled group, and a count above 0 tinted in its tone. */
const LEVEL_TONE: Record<Level, { title: string; row: string }> = {
  urgent: { title: 'text-destructive-strong', row: 'bg-destructive-soft text-destructive-strong' },
  operational: { title: 'text-primary-text', row: 'bg-secondary text-primary-text' },
  inventory: { title: 'text-warning', row: 'bg-warning-soft text-warning' },
};

const ROW = 'flex min-h-11 w-full items-center gap-3 rounded-md px-3 py-2 text-sm no-underline';

function Todo({ dashboard, order }: { dashboard: Dashboard; order: number }) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card aria-labelledby={titleId} className={cn('min-[860px]:col-span-3', className)} style={style}>
      <CardHeader>
        <CardTitle id={titleId}>{messages.todo.title}</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4 min-[860px]:grid-cols-3">
        {LEVELS.map(({ level, icon: Icon, entries }) => (
          <TodoLevel key={level} level={level} icon={Icon} entries={entries} dashboard={dashboard} />
        ))}
      </CardContent>
    </Card>
  );
}

function TodoLevel({ level, icon: Icon, entries, dashboard }: {
  level: Level;
  icon: LucideIcon;
  entries: readonly TodoEntry[];
  dashboard: Dashboard;
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
        {entries.map(({ id, to, count: countOf, label }) => {
          const count = countOf(dashboard);
          const content = (
            <>
              <span className="min-w-7 text-xl font-bold tabular-nums">{count.toLocaleString('es-AR')}</span>{' '}
              <span className="font-medium">{label(dashboard)}</span>
            </>
          );
          // A count of 0 still shows, without a link (UC-33), and quiet.
          return (
            <li key={id}>
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

const PERIODS: readonly SalesPeriod[] = ['TODAY', 'LAST_7_DAYS', 'MONTH', 'CUSTOM'];

/** The custom period's dates as typed, yyyy-mm-dd, empty while not chosen. */
interface DatesDraft {
  from: string;
  to: string;
}

type DatesProblem = keyof typeof messages.sales.invalid;

/** Days from one store date to another, both included. */
function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1;
}

/** UC-33's custom period rules, checked before asking (the API answers 400 to the same). */
function datesProblem({ from, to }: DatesDraft, today: string): DatesProblem | null {
  if (from === '' || to === '') {
    return 'missing';
  }
  if (from > to) {
    return 'order';
  }
  if (to > today) {
    return 'future';
  }
  return daysBetween(from, to) > 366 ? 'tooLong' : null;
}

function versusOf(sales: Dashboard['sales']): string {
  return sales.period === 'CUSTOM'
    ? messages.sales.versus.CUSTOM(daysBetween(sales.from, sales.to))
    : messages.sales.versus[sales.period];
}

const FIELD = 'min-h-11 rounded-md border border-input bg-card px-3 text-sm text-foreground aria-invalid:border-destructive';

function CustomDates({ draft, onDraft, problem, today }: {
  draft: DatesDraft;
  onDraft: (draft: DatesDraft) => void;
  problem: DatesProblem | null;
  today: string;
}) {
  const problemId = useId();
  const invalid = problem !== null;
  const described = invalid ? { 'aria-describedby': problemId, 'aria-invalid': true } : {};
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-3">
        <label className="flex flex-col gap-1 text-[13px] font-semibold text-muted-foreground">
          {messages.sales.from}
          <input
            type="date"
            className={FIELD}
            value={draft.from}
            max={today}
            onChange={(event) => {
              onDraft({ ...draft, from: event.target.value });
            }}
            {...described}
          />
        </label>
        <label className="flex flex-col gap-1 text-[13px] font-semibold text-muted-foreground">
          {messages.sales.to}
          <input
            type="date"
            className={FIELD}
            value={draft.to}
            max={today}
            onChange={(event) => {
              onDraft({ ...draft, to: event.target.value });
            }}
            {...described}
          />
        </label>
      </div>
      {/* Polite: it speaks once the dates are typed, not on every key */}
      <p id={problemId} aria-live="polite" className="m-0 min-h-5 text-[13px] font-semibold text-destructive-strong">
        {problem === null ? '' : messages.sales.invalid[problem]}
      </p>
    </div>
  );
}

interface SalesProps {
  sales: Dashboard['sales'];
  /** The period chosen on screen: while its figures load, the ones shown are still the previous period's. */
  period: SalesPeriod;
  onPeriod: (period: SalesPeriod) => void;
  draft: DatesDraft;
  onDraft: (draft: DatesDraft) => void;
  problem: DatesProblem | null;
  today: string;
  loading: boolean;
  order: number;
}

function Sales({ sales, period, onPeriod, draft, onDraft, problem, today, loading, order }: SalesProps) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card
      aria-labelledby={titleId}
      aria-busy={loading}
      className={cn('min-[860px]:col-span-3', className)}
      style={style}
    >
      <CardHeader>
        <div className="flex flex-col gap-0.5">
          <CardTitle id={titleId}>{messages.sales.title}</CardTitle>
          <CardDescription>
            {messages.sales.dates(formatDayMonth(sales.from), formatDayMonth(sales.to))}
          </CardDescription>
        </div>
        <SegmentedControl
          label={messages.sales.periodLabel}
          options={PERIODS.map((value) => ({ value, label: messages.sales.periods[value] }))}
          value={period}
          onValueChange={onPeriod}
          className="w-full min-[560px]:w-auto"
        />
      </CardHeader>
      {period === 'CUSTOM' && <CustomDates draft={draft} onDraft={onDraft} problem={problem} today={today} />}
      <CardContent
        className={cn('grid gap-3 transition-opacity min-[560px]:grid-cols-2', loading && 'opacity-60')}
      >
        <div className="flex flex-col gap-1 rounded-lg border border-border p-4">
          <p className="m-0 text-[13px] font-semibold text-muted-foreground">{messages.sales.periods[sales.period]}</p>
          <Figure value={formatMoney(sales.amountCents)} />
          <p className="m-0 flex flex-wrap gap-x-1.5 text-[13px]">
            <span className="text-muted-foreground">{messages.sales.orders(sales.orders)} ·</span>
            <Variation variation={sales.variation} versus={versusOf(sales)} />
          </p>
        </div>
        {/* The highlighted figure: the period's average ticket, tinted as the store's accent */}
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
            <Link to={LOW_STOCK_VARIANTS}>{messages.lowStock.seeAll}</Link>
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

/** The 5 newest entries of the activity log, its own read (UC-37); `Actualizar` and the auto-refresh read it too. */
const RECENT_ACTIVITY = 5;
const RECENT_ACTIVITY_KEY = ['admin', 'activity', 'recent'] as const;

function useRecentActivity(autoRefresh: boolean) {
  return useQuery({
    queryKey: RECENT_ACTIVITY_KEY,
    queryFn: async ({ signal }) => {
      const page = await fetchActivity({ area: null, from: null, to: null }, 0, signal);
      return page.items.slice(0, RECENT_ACTIVITY);
    },
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchInterval: autoRefresh ? AUTO_REFRESH_MS : false,
  });
}

/** Today's entries by their time; older ones by day and time. */
function whenOf(occurredAt: Date, today: string): string {
  return storeDay(occurredAt) === today ? formatTime(occurredAt) : formatDateTime(occurredAt);
}

function RecentActivity({ activity, today, order }: {
  activity: ReturnType<typeof useRecentActivity>;
  today: string;
  order: number;
}) {
  const titleId = useId();
  const { className, style } = rise(order);
  return (
    <Card aria-labelledby={titleId} aria-busy={activity.isFetching} className={className} style={style}>
      <CardHeader>
        <CardTitle id={titleId}>{messages.activity.title}</CardTitle>
        {activity.data !== undefined && activity.data.length > 0 && (
          <Button asChild variant="link" size="sm">
            <Link to="/admin/actividad">{messages.activity.seeAll}</Link>
          </Button>
        )}
      </CardHeader>
      <CardContent>
        {activity.isPending && <p className="m-0 text-sm text-muted-foreground">{messages.loading}</p>}
        {/* Only this block says so: the rest of the summary is another read (UC-33) */}
        {activity.isError && (
          <div className="flex flex-col items-start gap-2">
            <p className="m-0 text-sm">{messages.activity.loadError}</p>
            <Button size="sm" onClick={() => void activity.refetch()}>
              {esAdmin.retry}
            </Button>
          </div>
        )}
        {activity.data?.length === 0 && (
          <EmptyState icon={<History />} title={messages.activity.none} />
        )}
        {activity.data !== undefined && activity.data.length > 0 && (
          <ul className={ROWS}>
            {activity.data.map((entry) => {
              const label = subjectLabel(entry.subject);
              return (
                <li key={entry.id}>
                  <span className="flex min-w-0 flex-col">
                    <span className="text-sm font-semibold">{esAdmin.activityList.actions[entry.action]}</span>
                    {label !== null && <span className="text-[13px] text-muted-foreground">{label}</span>}
                  </span>
                  <time
                    dateTime={entry.occurredAt.toISOString()}
                    className="shrink-0 text-[13px] text-muted-foreground tabular-nums"
                  >
                    {whenOf(entry.occurredAt, today)}
                  </time>
                </li>
              );
            })}
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
 * opens, when the chart or the sales period changes, with "Actualizar", and every 5 minutes if the administrator
 * turned that on. One column at 360 px, three from 860 px (RNF-26).
 */
export function DashboardPage() {
  const [today] = useState(() => storeDay(new Date()));
  const [chart, setChart] = useState<Chart>('DAYS');
  const [period, setPeriod] = useState<SalesPeriod>('MONTH');
  const [draft, setDraft] = useState<DatesDraft>({ from: `${today.slice(0, 8)}01`, to: today });
  // The period asked for: a custom one only once its dates are valid, so a half-typed date never reaches the API.
  const [choice, setChoice] = useState<PeriodChoice>({ period: 'MONTH' });
  const [autoRefresh, setAutoRefresh] = useAutoRefresh();
  const dashboard = useDashboard(chart, choice, autoRefresh);
  const activity = useRecentActivity(autoRefresh);
  const switchId = useId();
  const problem = period === 'CUSTOM' ? datesProblem(draft, today) : null;

  const choosePeriod = (next: SalesPeriod) => {
    setPeriod(next);
    if (next !== 'CUSTOM') {
      setChoice({ period: next });
    } else if (datesProblem(draft, today) === null) {
      setChoice({ period: 'CUSTOM', ...draft });
    }
  };
  const changeDates = (next: DatesDraft) => {
    setDraft(next);
    if (datesProblem(next, today) === null) {
      setChoice({ period: 'CUSTOM', ...next });
    }
  };
  const fetching = dashboard.isFetching || activity.isFetching;

  return (
    <>
      <title>{`${messages.title} | ${esAdmin.title}`}</title>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h1>{messages.title}</h1>
          <p className="m-0 text-sm text-muted-foreground">{messages.intro}</p>
        </div>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <label htmlFor={switchId} className="flex min-h-11 cursor-pointer items-center gap-2 text-[13px] font-semibold">
            <Switch id={switchId} checked={autoRefresh} onCheckedChange={setAutoRefresh} />
            {messages.autoRefresh}
          </label>
          {dashboard.data !== undefined && (
            <p className="m-0 text-[13px] text-muted-foreground tabular-nums">
              {messages.updatedAt(formatTime(new Date(dashboard.dataUpdatedAt)))}
            </p>
          )}
          {/* Focusable while it reads (aria-disabled), so a keyboard does not lose its place. */}
          <Button
            variant="secondary"
            aria-disabled={fetching}
            onClick={() => {
              if (!fetching) {
                void dashboard.refetch();
                void activity.refetch();
              }
            }}
          >
            <RefreshCw aria-hidden="true" className={cn(fetching && 'motion-safe:animate-spin')} />
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
          <Todo dashboard={dashboard.data} order={0} />
          <Sales
            sales={dashboard.data.sales}
            period={period}
            onPeriod={choosePeriod}
            draft={draft}
            onDraft={changeDates}
            problem={problem}
            today={today}
            loading={dashboard.isPlaceholderData}
            order={1}
          />
          <ChartBlock
            chart={dashboard.data.chart}
            requested={chart}
            loading={dashboard.isPlaceholderData}
            onKind={setChart}
            order={2}
          />
          <QuickActions order={3} />
          <RecentOrders orders={dashboard.data.recentOrders} order={4} />
          <RecentActivity activity={activity} today={today} order={5} />
          <LowStock lowStock={dashboard.data.lowStock} order={6} />
          <BestSellers bestSellers={dashboard.data.bestSellers} order={7} />
          <Totals totals={dashboard.data.totals} order={8} />
        </div>
      )}
    </>
  );
}
