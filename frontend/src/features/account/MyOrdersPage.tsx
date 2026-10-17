import { useInfiniteQuery } from '@tanstack/react-query';
import { Link, Navigate } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { ORDERS_PATH, SIGN_IN_PATH } from './AccountLink.tsx';
import { fetchMyOrders, MY_ORDERS_KEY } from './myOrdersApi.ts';
import { useCustomer } from './useCustomer.ts';
import './account.css';

const messages = es.account.orders;

/**
 * UC-22 (T-E4-07): the signed-in customer's orders at /cuenta/pedidos, newest first, 20 at a time with "Ver más"; a
 * visitor who is not signed in goes to sign in.
 */
export function MyOrdersPage() {
  const customer = useCustomer();
  const orders = useInfiniteQuery({
    queryKey: MY_ORDERS_KEY,
    queryFn: ({ pageParam, signal }) => fetchMyOrders(pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, all) => (last?.hasMore === true ? all.length : undefined),
    enabled: customer !== undefined && customer !== null,
  });

  if (customer === null || orders.data?.pages.some((page) => page === null) === true) {
    return <Navigate to={SIGN_IN_PATH} replace />;
  }

  const list = orders.data?.pages.flatMap((page) => page?.orders ?? []);

  return (
    <main className="wrap account-page my-orders">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      {orders.isPending && <p role="status">{messages.loading}</p>}
      {orders.isError && list === undefined && (
        <div className="address-load-error" role="alert">
          <p>{messages.loadError}</p>
          <button type="button" className="btn" onClick={() => void orders.refetch()}>
            {messages.retry}
          </button>
        </div>
      )}
      {list?.length === 0 && (
        <div className="empty-state">
          <p>{messages.empty}</p>
          <Link to="/">{messages.goShopping}</Link>
        </div>
      )}
      {list !== undefined && list.length > 0 && (
        <ul className="my-order-list">
          {list.map((order) => (
            <li key={order.id}>
              <Link to={`${ORDERS_PATH}/${order.id}`} className="my-order-card">
                <span className="my-order-number">{es.tracking.title(order.number)}</span>
                <span className={`my-order-status status-${order.status.toLowerCase()}`}>
                  {es.tracking.statuses[order.status]}
                </span>
                <span className="my-order-date">{messages.placedOn(formatDate(order.createdAt))}</span>
                <span className="my-order-total">{formatMoney(order.totalCents)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      {orders.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary"
          disabled={orders.isFetchingNextPage}
          onClick={() => void orders.fetchNextPage()}
        >
          {orders.isFetchingNextPage ? messages.loadingMore : messages.more}
        </button>
      )}
      {orders.isFetchNextPageError && (
        <p className="address-load-error" role="alert">
          {messages.moreError}
        </p>
      )}
    </main>
  );
}
