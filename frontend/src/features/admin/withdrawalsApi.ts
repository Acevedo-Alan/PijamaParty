import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn, problemText } from './adminSession.ts';
import type { OrderStatus } from './ordersApi.ts';

type SummaryResponse = components['schemas']['WithdrawalSummaryResponse'];

export type WithdrawalStatus = NonNullable<SummaryResponse['status']>;

/** Received (still to arrange) first in the filter, then resolved (UC-29). */
export const WITHDRAWAL_STATUSES: readonly WithdrawalStatus[] = ['RECEIVED', 'RESOLVED'];

/** A row of the back-office list of withdrawal requests (UC-29), with its order. */
export interface WithdrawalSummary {
  id: string;
  code: string;
  orderId: string;
  orderNumber: number;
  orderStatus: OrderStatus;
  buyerName: string;
  createdAt: Date;
  /** Requested after the 10-day term: recorded so the administrator decides. */
  outOfTerm: boolean;
  status: WithdrawalStatus;
  resolvedAt: Date | null;
}

export type ResolveAnswer =
  | { status: 'resolved' }
  /** Someone resolved it since the list was read. */
  | { status: 'alreadyResolved' }
  | { status: 'notFound' };

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

function toSummary(row: SummaryResponse): WithdrawalSummary {
  const { id, code, orderId, orderNumber, orderStatus, buyerName, createdAt, outOfTerm, status } = row;
  if (
    id === undefined ||
    code === undefined ||
    orderId === undefined ||
    orderNumber === undefined ||
    orderStatus === undefined ||
    buyerName === undefined ||
    createdAt === undefined ||
    outOfTerm === undefined ||
    status === undefined
  ) {
    throw new Error('Incomplete withdrawal summary');
  }
  return {
    id,
    code,
    orderId,
    orderNumber,
    orderStatus,
    buyerName,
    createdAt: new Date(createdAt),
    outOfTerm,
    status,
    resolvedAt: row.resolvedAt ? new Date(row.resolvedAt) : null,
  };
}

/** One page of withdrawal requests, newest first, only those in {@code status} when given. */
export async function fetchWithdrawals(
  status: WithdrawalStatus | null,
  page: number,
  signal?: AbortSignal,
): Promise<{ items: WithdrawalSummary[]; hasMore: boolean }> {
  const { data, response } = await api.GET('/api/admin/withdrawals', {
    params: { query: status === null ? { page } : { status, page } },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (data === undefined) {
    throw failed('Withdrawal list', response);
  }
  return { items: (data.items ?? []).map(toSummary), hasMore: data.hasMore === true };
}

export async function resolveWithdrawal(id: string): Promise<ResolveAnswer> {
  const { data, error, response } = await api.POST('/api/admin/withdrawals/{id}/resolve', {
    params: { path: { id } },
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (response.status === 409 && problemText(error, 'problem') === 'WITHDRAWAL_ALREADY_RESOLVED') {
    return { status: 'alreadyResolved' };
  }
  if (data?.status !== 'RESOLVED') {
    throw failed('Withdrawal resolution', response);
  }
  return { status: 'resolved' };
}
