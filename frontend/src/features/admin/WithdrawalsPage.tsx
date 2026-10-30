import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDateTime } from '../../i18n/formatDate.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import {
  fetchWithdrawals,
  resolveWithdrawal,
  WITHDRAWAL_STATUSES,
  type WithdrawalStatus,
  type WithdrawalSummary,
} from './withdrawalsApi.ts';

const messages = esAdmin.withdrawalList;
const STATUS_PARAM = 'estado';

/** The status in the address, so the filter survives opening an order and coming back; null for every request. */
function statusOf(params: URLSearchParams): WithdrawalStatus | null {
  const value = params.get(STATUS_PARAM);
  return WITHDRAWAL_STATUSES.find((status) => status === value) ?? null;
}

/**
 * UC-29 (T-E3-26) at /admin/arrepentimientos: the withdrawal requests, newest first, 50 at a time, filtered by status
 * if one is chosen, each with its order, the out-of-term mark and, while received, a button to resolve it.
 */
export function WithdrawalsPage() {
  const filterId = useId();
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [notice, setNotice] = useState<Notice | null>(null);
  const [resolving, setResolving] = useState<string | null>(null);
  const status = statusOf(params);
  const withdrawals = useInfiniteQuery({
    queryKey: ['admin', 'withdrawals', 'list', status],
    queryFn: ({ pageParam, signal }) => fetchWithdrawals(status, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => (last.hasMore ? page + 1 : undefined),
  });
  const items = withdrawals.data?.pages.flatMap((page) => page.items) ?? [];

  async function resolve(withdrawal: WithdrawalSummary) {
    setNotice(null);
    setResolving(withdrawal.id);
    try {
      const answer = await resolveWithdrawal(withdrawal.id);
      setNotice(
        answer.status === 'resolved'
          ? { kind: 'ok', text: messages.resolved(withdrawal.code) }
          : { kind: 'error', text: answer.status === 'alreadyResolved' ? messages.alreadyResolved : messages.notFound },
      );
      await queryClient.invalidateQueries({ queryKey: ['admin', 'withdrawals'] });
      await queryClient.invalidateQueries({ queryKey: ['admin', 'orders', 'detail', withdrawal.orderId] });
    } catch {
      setNotice({ kind: 'error', text: esAdmin.actionError });
    } finally {
      setResolving(null);
    }
  }

  return (
    <>
      <title>{`${messages.title} | ${esAdmin.title}`}</title>
      <div className="admin-title-row">
        <h1>{messages.title}</h1>
      </div>
      <div className="field order-filter">
        <label htmlFor={filterId}>{messages.filter}</label>
        <select
          id={filterId}
          value={status ?? ''}
          onChange={(event) => {
            const next = event.target.value;
            setNotice(null);
            setParams(next === '' ? {} : { [STATUS_PARAM]: next }, { replace: true });
          }}
        >
          <option value="">{messages.all}</option>
          {WITHDRAWAL_STATUSES.map((value) => (
            <option key={value} value={value}>
              {messages.statuses[value]}
            </option>
          ))}
        </select>
      </div>
      <Feedback notice={notice} />
      {withdrawals.isPending && <p role="status">{esAdmin.loading}</p>}
      {withdrawals.isError && items.length === 0 && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void withdrawals.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      )}
      {withdrawals.isSuccess && items.length === 0 && (
        <p>{status === null ? messages.empty : messages.emptyInStatus}</p>
      )}
      {items.length > 0 && (
        <ul className="admin-list">
          {items.map((withdrawal) => (
            <li key={withdrawal.id} className="admin-card">
              <div className="admin-card-head">
                <span className="admin-card-title">{withdrawal.code}</span>
                <span className="admin-badge">{messages.statuses[withdrawal.status]}</span>
                {withdrawal.outOfTerm && <span className="admin-badge withdrawal-late">{messages.outOfTerm}</span>}
              </div>
              <p className="admin-card-meta">
                <Link to={`/admin/pedidos/${withdrawal.orderId}`}>{messages.order(withdrawal.orderNumber)}</Link> ·{' '}
                {es.tracking.statuses[withdrawal.orderStatus]}
              </p>
              <p className="admin-card-meta">
                {messages.requested(withdrawal.buyerName, formatDateTime(withdrawal.createdAt))}
                {withdrawal.resolvedAt !== null && ` · ${messages.resolvedAt(formatDateTime(withdrawal.resolvedAt))}`}
              </p>
              {withdrawal.status === 'RECEIVED' && (
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={resolving !== null}
                  onClick={() => void resolve(withdrawal)}
                >
                  {resolving === withdrawal.id ? messages.resolving : messages.resolve}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {withdrawals.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary show-more-btn"
          disabled={withdrawals.isFetchingNextPage}
          onClick={() => void withdrawals.fetchNextPage()}
        >
          {withdrawals.isFetchingNextPage ? esAdmin.loading : esAdmin.showMore}
        </button>
      )}
    </>
  );
}
