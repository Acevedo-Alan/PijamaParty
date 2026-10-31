import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client.ts';
import { ensureSignedIn } from './adminSession.ts';

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
