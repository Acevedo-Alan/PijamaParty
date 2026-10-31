import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn } from './adminSession.ts';
import type { OrderStatus } from './ordersApi.ts';

/** The menu's counts (T-E8-05): read when the back office opens and again when the order list loads. */
export const WORKLOAD_COUNTS_KEY = ['admin', 'dashboard', 'counts'] as const;

export interface WorkloadCounts {
  /** Orders PAID, waiting to be prepared: the badge on "Pedidos". */
  paidOrders: number;
}

export async function fetchWorkloadCounts(signal?: AbortSignal): Promise<WorkloadCounts> {
  const { data, response } = await api.GET('/api/admin/dashboard/counts', { signal: signal ?? null });
  ensureSignedIn(response);
  if (data === undefined) {
    throw new Error(`Workload counts failed with status ${String(response.status)}`);
  }
  return { paidOrders: data.paidOrders ?? 0 };
}

/** Without the counts the menu just shows no badge: a failed read never blocks the back office. */
export function useWorkloadCounts() {
  return useQuery({
    queryKey: WORKLOAD_COUNTS_KEY,
    queryFn: ({ signal }) => fetchWorkloadCounts(signal),
    retry: false,
  });
}

/** UC-33: the chart by the last 30 days or the last 12 months. */
export type Chart = 'DAYS' | 'MONTHS';

export interface PeriodSales {
  amountCents: number;
  orders: number;
  /** Whole percents against the period before; null ("—") when nothing sold then. */
  variation: number | null;
}

export interface ChartPoint {
  /** A day (2026-10-31) or a month (2026-10). */
  period: string;
  amountCents: number;
  orders: number;
}

export interface LowStockItem {
  variantId: string;
  productName: string;
  size: string;
  color: string;
  available: number;
}

export interface BestSeller {
  productSlug: string;
  productName: string;
  units: number;
  amountCents: number;
}

export interface RecentOrder {
  id: string;
  number: number;
  createdAt: Date;
  buyerName: string;
  status: OrderStatus;
  totalCents: number;
}

export interface Dashboard {
  todo: {
    paidOrders: number;
    preparingOrders: number;
    receivedWithdrawals: number;
    pendingReviews: number;
    emailAlerts: number;
    soldOutVariants: number;
  };
  sales: { today: PeriodSales; lastWeek: PeriodSales; month: PeriodSales; averageTicketCents: number | null };
  chart: { kind: Chart; points: ChartPoint[] };
  lowStock: { threshold: number; count: number; items: LowStockItem[] };
  bestSellers: BestSeller[];
  recentOrders: RecentOrder[];
  totals: { publishedProducts: number };
}

type Period = components['schemas']['PeriodResponse'];

function period(value: Period | undefined): PeriodSales {
  return { amountCents: value?.amountCents ?? 0, orders: value?.orders ?? 0, variation: value?.variation ?? null };
}

function toDashboard(data: components['schemas']['DashboardResponse'], chart: Chart): Dashboard {
  const todo = data.todo ?? {};
  return {
    todo: {
      paidOrders: todo.paidOrders ?? 0,
      preparingOrders: todo.preparingOrders ?? 0,
      receivedWithdrawals: todo.receivedWithdrawals ?? 0,
      pendingReviews: todo.pendingReviews ?? 0,
      emailAlerts: todo.emailAlerts ?? 0,
      soldOutVariants: todo.soldOutVariants ?? 0,
    },
    sales: {
      today: period(data.sales?.today),
      lastWeek: period(data.sales?.lastWeek),
      month: period(data.sales?.month),
      averageTicketCents: data.sales?.averageTicketCents ?? null,
    },
    chart: {
      kind: data.chart?.kind ?? chart,
      points: (data.chart?.points ?? []).map((point) => ({
        period: point.period ?? '',
        amountCents: point.amountCents ?? 0,
        orders: point.orders ?? 0,
      })),
    },
    lowStock: {
      threshold: data.lowStock?.threshold ?? 0,
      count: data.lowStock?.count ?? 0,
      items: (data.lowStock?.items ?? []).map((item) => ({
        variantId: item.variantId ?? '',
        productName: item.productName ?? '',
        size: item.size ?? '',
        color: item.color ?? '',
        available: item.available ?? 0,
      })),
    },
    bestSellers: (data.bestSellers ?? []).map((sold) => ({
      productSlug: sold.productSlug ?? '',
      productName: sold.productName ?? '',
      units: sold.units ?? 0,
      amountCents: sold.amountCents ?? 0,
    })),
    recentOrders: (data.recentOrders ?? []).map((order) => ({
      id: order.id ?? '',
      number: order.number ?? 0,
      createdAt: new Date(order.createdAt ?? 0),
      buyerName: order.buyerName ?? '',
      status: order.status ?? 'AWAITING_PAYMENT',
      totalCents: order.totalCents ?? 0,
    })),
    totals: { publishedProducts: data.totals?.publishedProducts ?? 0 },
  };
}

export async function fetchDashboard(chart: Chart, signal?: AbortSignal): Promise<Dashboard> {
  const { data, response } = await api.GET('/api/admin/dashboard', {
    params: { query: { chart } },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (data === undefined) {
    throw new Error(`Dashboard failed with status ${String(response.status)}`);
  }
  return toDashboard(data, chart);
}

/**
 * UC-33: read when the page opens and again with "Actualizar"; nothing refreshes by itself (no refetch on focus or
 * on a timer). The menu's count is the same read, so it follows the summary.
 */
export function useDashboard(chart: Chart) {
  const queryClient = useQueryClient();
  return useQuery({
    queryKey: ['admin', 'dashboard', 'summary', chart],
    queryFn: async ({ signal }) => {
      const dashboard = await fetchDashboard(chart, signal);
      // The menu's badge comes from the same read, without a request of its own (T-E8-05).
      queryClient.setQueryData<WorkloadCounts>(WORKLOAD_COUNTS_KEY, { paidOrders: dashboard.todo.paidOrders });
      return dashboard;
    },
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    // Switching days and months keeps the figures on screen until the other chart arrives.
    placeholderData: keepPreviousData,
  });
}
