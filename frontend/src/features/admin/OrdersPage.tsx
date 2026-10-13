import { useInfiniteQuery } from '@tanstack/react-query';
import { useId } from 'react';
import { Link, useSearchParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatDateTime } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { fetchOrders, ORDER_STATUSES, type OrderStatus } from './ordersApi.ts';

const messages = es.admin.orderList;
const STATUS_PARAM = 'estado';

/** The status in the address, so the filter survives opening an order and coming back; null for every order. */
function statusOf(params: URLSearchParams): OrderStatus | null {
  const value = params.get(STATUS_PARAM);
  return ORDER_STATUSES.find((status) => status === value) ?? null;
}

/** UC-18 at /admin/pedidos: the orders, newest first, 50 at a time, filtered by status if one is chosen. */
export function OrdersPage() {
  const filterId = useId();
  const [params, setParams] = useSearchParams();
  const status = statusOf(params);
  const orders = useInfiniteQuery({
    queryKey: ['admin', 'orders', 'list', status],
    queryFn: ({ pageParam, signal }) => fetchOrders(status, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => (last.hasMore ? page + 1 : undefined),
  });
  const items = orders.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <>
      <title>{`${messages.title} | ${es.admin.title}`}</title>
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
            setParams(next === '' ? {} : { [STATUS_PARAM]: next }, { replace: true });
          }}
        >
          <option value="">{messages.all}</option>
          {ORDER_STATUSES.map((value) => (
            <option key={value} value={value}>
              {es.tracking.statuses[value]}
            </option>
          ))}
        </select>
      </div>
      {orders.isPending && <p role="status">{es.admin.loading}</p>}
      {orders.isError && items.length === 0 && (
        <div role="alert">
          <p>{es.admin.loadError}</p>
          <button type="button" className="btn" onClick={() => void orders.refetch()}>
            {es.admin.retry}
          </button>
        </div>
      )}
      {orders.isSuccess && items.length === 0 && <p>{status === null ? messages.empty : messages.emptyInStatus}</p>}
      {items.length > 0 && (
        <ul className="admin-list">
          {items.map((order) => (
            <li key={order.id} className="admin-card">
              <div className="admin-card-head">
                <Link to={`/admin/pedidos/${order.id}`} className="admin-card-title">
                  {messages.number(order.number)}
                </Link>
                <span className="admin-badge">{es.tracking.statuses[order.status]}</span>
                <span className="order-total">{formatMoney(order.totalCents)}</span>
              </div>
              <p className="admin-card-meta">
                {messages.buyer(order.buyerName, order.city, es.provinces[order.province])} ·{' '}
                {formatDateTime(order.createdAt)}
              </p>
            </li>
          ))}
        </ul>
      )}
      {orders.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary show-more-btn"
          disabled={orders.isFetchingNextPage}
          onClick={() => void orders.fetchNextPage()}
        >
          {orders.isFetchingNextPage ? es.admin.loading : es.admin.showMore}
        </button>
      )}
    </>
  );
}
