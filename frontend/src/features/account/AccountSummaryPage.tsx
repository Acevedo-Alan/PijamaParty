import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { Customer } from './accountApi.ts';
import { ADDRESSES_PATH, DATA_PATH, ORDERS_PATH, SIGN_IN_PATH } from './AccountLink.tsx';
import { ADDRESSES_KEY, fetchAddresses } from './addressBookApi.ts';
import { addressLine, addressPlace } from './addressText.ts';
import { fetchMyOrders, MY_ORDERS_KEY } from './myOrdersApi.ts';
import { firstName, useCustomer } from './useCustomer.ts';
import './account.css';

const messages = es.account.summary;

/** How many of the newest orders the summary shows. */
const RECENT_ORDERS = 3;

function Block({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="account-block" aria-label={title}>
      <h2>{title}</h2>
      {children}
    </section>
  );
}

/** "Ver todos →" and the like: a link at the end of a block, its arrow decorative. */
function MoreLink({ to, children }: { to: string; children: string }) {
  return (
    <Link to={to} className="account-block-link">
      {children} <span aria-hidden="true">→</span>
    </Link>
  );
}

function LoadError({ text, onRetry }: { text: string; onRetry: () => void }) {
  return (
    <div className="address-load-error" role="alert">
      <p>{text}</p>
      <button type="button" className="btn btn-secondary" onClick={onRetry}>
        {messages.retry}
      </button>
    </div>
  );
}

/** "Pedidos recientes": the newest orders, each opening its detail, with the same query as "Mis pedidos". */
function RecentOrders() {
  const orders = useInfiniteQuery({
    queryKey: MY_ORDERS_KEY,
    queryFn: ({ pageParam, signal }) => fetchMyOrders(pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, all) => (last?.hasMore === true ? all.length : undefined),
  });
  const first = orders.data?.pages[0];
  if (first === null) {
    return <Navigate to={SIGN_IN_PATH} replace />;
  }
  const recent = first?.orders.slice(0, RECENT_ORDERS);
  return (
    <Block title={messages.recentOrders}>
      {orders.isPending && <p role="status">{messages.loading}</p>}
      {orders.isError && recent === undefined && (
        <LoadError text={messages.ordersError} onRetry={() => void orders.refetch()} />
      )}
      {recent?.length === 0 && (
        <>
          <p className="account-block-empty">{es.account.orders.empty}</p>
          <Link to="/" className="account-block-link">
            {es.account.orders.goShopping}
          </Link>
        </>
      )}
      {recent !== undefined && recent.length > 0 && (
        <>
          <ul className="my-order-list">
            {recent.map((order) => (
              <li key={order.id}>
                <Link to={`${ORDERS_PATH}/${order.id}`} className="my-order-card">
                  <span className="my-order-number">{es.tracking.title(order.number)}</span>
                  <span className={`my-order-status status-${order.status.toLowerCase()}`}>
                    {es.tracking.statuses[order.status]}
                  </span>
                  <span className="my-order-date">{es.account.orders.placedOn(formatDate(order.createdAt))}</span>
                  <span className="my-order-total">{formatMoney(order.totalCents)}</span>
                </Link>
              </li>
            ))}
          </ul>
          <MoreLink to={ORDERS_PATH}>{messages.allOrders}</MoreLink>
        </>
      )}
    </Block>
  );
}

/** "Dirección principal": the address the checkout picks first (UC-20, UC-21), with the address book's query. */
function PrimaryAddress() {
  const addresses = useQuery({ queryKey: ADDRESSES_KEY, queryFn: ({ signal }) => fetchAddresses(signal) });
  if (addresses.data === null) {
    return <Navigate to={SIGN_IN_PATH} replace />;
  }
  const primary = addresses.data?.find((address) => address.primary);
  return (
    <Block title={messages.primaryAddress}>
      {addresses.isPending && <p role="status">{messages.loading}</p>}
      {addresses.isError && addresses.data === undefined && (
        <LoadError text={messages.addressesError} onRetry={() => void addresses.refetch()} />
      )}
      {addresses.data !== undefined &&
        (primary === undefined ? (
          <>
            <p className="account-block-empty">{messages.noAddresses}</p>
            <MoreLink to={ADDRESSES_PATH}>{messages.addAddress}</MoreLink>
          </>
        ) : (
          <>
            <p className="address-line">{addressLine(primary)}</p>
            <p className="address-place">{addressPlace(primary)}</p>
            <MoreLink to={ADDRESSES_PATH}>{messages.manageAddresses}</MoreLink>
          </>
        ))}
    </Block>
  );
}

/** "Tus datos": what the account knows of the customer; the phone is the one it saved (UC-40) or its last purchase left (UC-21). */
function YourData({ customer }: { customer: Customer }) {
  return (
    <Block title={messages.yourData}>
      <dl className="account-data-list">
        <div>
          <dt>{messages.name}</dt>
          <dd>{customer.name}</dd>
        </div>
        <div>
          <dt>{es.account.email}</dt>
          <dd>{customer.email}</dd>
        </div>
        <div>
          <dt>{messages.phone}</dt>
          <dd>{customer.phone ?? <span className="account-data-missing">{messages.noPhone}</span>}</dd>
        </div>
      </dl>
      <MoreLink to={DATA_PATH}>{messages.dataAndAccess}</MoreLink>
    </Block>
  );
}

/**
 * T-E7-61: the summary at /cuenta, what a customer comes for first: its newest orders, its primary address and its
 * data, each block loading and failing on its own. The backend still sends the browser back to /cuenta?social=… after
 * linking an account or confirming with it (UC-27, UC-28): that goes on to "Datos y acceso", where its notice shows.
 */
export function AccountSummaryPage() {
  const customer = useCustomer();
  const [searchParams] = useSearchParams();
  const social = searchParams.get('social');
  if (social !== null) {
    return <Navigate to={`${DATA_PATH}?${new URLSearchParams({ social }).toString()}`} replace />;
  }
  if (customer === null) {
    return <Navigate to={SIGN_IN_PATH} replace />;
  }
  // The orders and the address start at once, beside the customer's own request: they need nothing of its answer,
  // and each sends a visitor who is not signed in to sign in by itself.
  return (
    <div className="account-page account-summary">
      <title>{`${es.account.myAccountLink} | ${es.store.name}`}</title>
      <h1>{customer === undefined ? es.account.myAccountLink : es.account.greeting(firstName(customer.name))}</h1>
      <RecentOrders />
      <PrimaryAddress />
      {customer !== undefined && <YourData customer={customer} />}
    </div>
  );
}
