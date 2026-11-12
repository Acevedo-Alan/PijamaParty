import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Navigate, useSearchParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { Customer } from './accountApi.ts';
import { ADDRESSES_PATH, DATA_PATH, ORDERS_PATH, SIGN_IN_PATH } from './AccountLink.tsx';
import { ADDRESSES_KEY, fetchAddresses } from './addressBookApi.ts';
import { addressLine, addressPlace } from './addressText.ts';
import { Group } from './Group.tsx';
import { fetchMyOrders, MY_ORDERS_KEY } from './myOrdersApi.ts';
import { OrderCard } from './OrderCard.tsx';
import { firstName, useCustomer } from './useCustomer.ts';
import './account.css';

const messages = es.account.summary;

/** How many of the newest orders the summary shows. */
const RECENT_ORDERS = 3;

function LoadError({ text, onRetry }: { text: string; onRetry: () => void }) {
  return (
    <div className="group-row address-load-error" role="alert">
      <p>{text}</p>
      <button type="button" className="btn btn-secondary" onClick={onRetry}>
        {messages.retry}
      </button>
    </div>
  );
}

function Loading() {
  return (
    <p className="group-row group-missing" role="status">
      {messages.loading}
    </p>
  );
}

/** "Pedidos recientes": the newest orders, each a row opening its detail, with the same query as "Mis pedidos". */
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
    <Group.Section title={messages.recentOrders}>
      <Group.Card>
        {orders.isPending && <Loading />}
        {orders.isError && recent === undefined && (
          <LoadError text={messages.ordersError} onRetry={() => void orders.refetch()} />
        )}
        {recent?.length === 0 && (
          <>
            <p className="group-row group-missing">{es.account.orders.empty}</p>
            <Group.ActionLink to="/" icon="parcel">
              {es.account.orders.goShopping}
            </Group.ActionLink>
          </>
        )}
        {recent !== undefined &&
          recent.length > 0 &&
          recent.map((order) => (
            <Group.RowLink key={order.id} to={`${ORDERS_PATH}/${order.id}`}>
              <span className="summary-order">
                <span className="summary-order-number">{es.tracking.title(order.number)}</span>
                <span className="summary-order-total">{formatMoney(order.totalCents)}</span>
                <span className="summary-order-status">
                  <OrderCard.StatusDot status={order.status} />
                  {`${es.tracking.statuses[order.status]} · ${formatDate(order.createdAt)}`}
                </span>
              </span>
            </Group.RowLink>
          ))}
        {recent !== undefined && recent.length > 0 && (
          <Group.ActionLink to={ORDERS_PATH} icon="parcel">
            {messages.allOrders}
          </Group.ActionLink>
        )}
      </Group.Card>
    </Group.Section>
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
    <Group.Section title={messages.primaryAddress}>
      <Group.Card>
        {addresses.isPending && <Loading />}
        {addresses.isError && addresses.data === undefined && (
          <LoadError text={messages.addressesError} onRetry={() => void addresses.refetch()} />
        )}
        {addresses.data !== undefined &&
          (primary === undefined ? (
            <>
              <p className="group-row group-missing">{messages.noAddresses}</p>
              <Group.ActionLink to={ADDRESSES_PATH} icon="plus">
                {messages.addAddress}
              </Group.ActionLink>
            </>
          ) : (
            <>
              <div className="group-row">
                <p className="address-line">{addressLine(primary)}</p>
                <p className="address-place">{addressPlace(primary)}</p>
              </div>
              <Group.ActionLink to={ADDRESSES_PATH} icon="pin">
                {messages.manageAddresses}
              </Group.ActionLink>
            </>
          ))}
      </Group.Card>
    </Group.Section>
  );
}

/** "Tus datos": what the account knows of the customer; the phone is the one it saved (UC-40) or its last purchase left (UC-21). */
function YourData({ customer }: { customer: Customer }) {
  return (
    <Group.Section title={messages.yourData}>
      <Group.Card>
        <Group.Field label={messages.name}>{customer.name}</Group.Field>
        <Group.Field label={es.account.email}>{customer.email}</Group.Field>
        <Group.Field label={messages.phone}>
          {customer.phone ?? <Group.Missing>{messages.noPhone}</Group.Missing>}
        </Group.Field>
        <Group.ActionLink to={DATA_PATH} icon="person">
          {messages.dataAndAccess}
        </Group.ActionLink>
      </Group.Card>
    </Group.Section>
  );
}

/**
 * T-E7-61: the summary at /cuenta, what a customer comes for first: its newest orders, its primary address and its
 * data, each block loading and failing on its own; T-E7-63 draws them as grouped lists. The backend still sends the
 * browser back to /cuenta?social=… after linking an account or confirming with it (UC-27, UC-28): that goes on to
 * "Datos y acceso", where its notice shows.
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
      <p className="account-intro">{messages.intro}</p>
      <RecentOrders />
      <PrimaryAddress />
      {customer !== undefined && <YourData customer={customer} />}
    </div>
  );
}
