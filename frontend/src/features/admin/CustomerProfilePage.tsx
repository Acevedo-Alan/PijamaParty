import { useInfiniteQuery } from '@tanstack/react-query';
import { Check, Copy, Mail, MessageCircle } from 'lucide-react';
import { useEffect, useId, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { Badge } from '../../components/ui/Badge.tsx';
import { Button } from '../../components/ui/Button.tsx';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/Card.tsx';
import { cn } from '../../components/ui/cn.ts';
import { EmptyState } from '../../components/ui/EmptyState.tsx';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDate, formatDateTime } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { mailLink, whatsAppLink } from './contactLinks.ts';
import {
  fetchCustomerProfile,
  type CustomerAddress,
  type CustomerOrder,
  type CustomerProfile,
} from './customersApi.ts';
import { profileReturn, useBackToList, type ListReturn, type ProfileReturn } from './listReturn.ts';
import { rise } from './rise.ts';

const messages = esAdmin.customerProfile;
const CUSTOMER_LIST = '/admin/clientes';
/** The 24 jurisdictions by constant; an unknown one shows as it came. */
const PROVINCES: Partial<Record<string, string>> = es.provinces;

/**
 * UC-34 at /admin/clientes/:customerId (T-E8-11): a customer's profile, read-only, laid out as owners know it from
 * other e-commerce back offices (Jakob's law): from 860 px the purchase figures and the orders on the left, the contact
 * and the addresses in a side column; on a phone they stack in that order. The orders are the rows of "Pedidos", 20 at
 * a time. Opening the profile is recorded in "Actividad" by the server; loading more orders is not.
 */
export function CustomerProfilePage() {
  const { customerId = '' } = useParams();
  const back = useBackToList(CUSTOMER_LIST);
  const profile = useInfiniteQuery({
    queryKey: ['admin', 'customers', 'profile', customerId],
    queryFn: ({ pageParam, signal }) => fetchCustomerProfile(customerId, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) =>
      last.status === 'found' && last.profile.hasMoreOrders ? page + 1 : undefined,
  });
  const first = profile.data?.pages[0];
  const orders = profile.data?.pages.flatMap((page) => (page.status === 'found' ? page.profile.orders : [])) ?? [];

  return (
    <>
      <Link to={back.to} className="back-link">
        {messages.backToList}
      </Link>
      {profile.isPending && <p role="status">{esAdmin.loading}</p>}
      {profile.isError && first === undefined && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void profile.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      )}
      {first?.status === 'notFound' && (
        <>
          <title>{`${esAdmin.customers} | ${esAdmin.title}`}</title>
          <p>{messages.notFound}</p>
        </>
      )}
      {first?.status === 'found' && (
        <ProfileView customer={first.profile} orders={orders} listState={back.state}>
          {profile.isFetchNextPageError && <p role="alert">{esAdmin.loadError}</p>}
          {profile.hasNextPage && (
            <button
              type="button"
              className="btn btn-secondary show-more-btn"
              disabled={profile.isFetchingNextPage}
              onClick={() => void profile.fetchNextPage()}
            >
              {profile.isFetchingNextPage ? esAdmin.loading : esAdmin.showMore}
            </button>
          )}
        </ProfileView>
      )}
    </>
  );
}

interface ProfileViewProps {
  customer: CustomerProfile;
  orders: CustomerOrder[];
  /** The filters of "Clientes" this profile was opened from, for the way back from its orders. */
  listState: ListReturn;
  /** What follows the orders: "Ver más" and its error. */
  children: ReactNode;
}

function ProfileView({ customer, orders, listState, children }: ProfileViewProps) {
  // An order opened here returns here, and this profile still returns to the filtered list (UC-18, UC-34).
  const orderState = profileReturn(`${CUSTOMER_LIST}/${customer.id}`, listState);
  return (
    <>
      <title>{`${customer.name} | ${esAdmin.title}`}</title>
      <div className="admin-title-row">
        <div>
          <h1>{customer.name}</h1>
          <p className="admin-card-meta">{messages.since(formatDate(customer.registeredAt))}</p>
        </div>
      </div>
      <div className="grid items-start gap-4 min-[860px]:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-4">
          <Figures customer={customer} />
          <Orders customer={customer} orders={orders} orderState={orderState}>
            {children}
          </Orders>
        </div>
        <div className="flex min-w-0 flex-col gap-4">
          <Contact customer={customer} />
          <Addresses addresses={customer.addresses} />
        </div>
      </div>
    </>
  );
}

/** The figure of a block, the largest thing in it (D-E8-12). */
function Figure({
  label,
  value,
  detail,
  muted = false,
}: {
  label: string;
  value: string;
  detail?: string;
  muted?: boolean;
}) {
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <dt className="text-[13px] text-muted-foreground">{label}</dt>
      <dd
        className={cn(
          'm-0 text-[26px] leading-tight font-bold whitespace-nowrap tabular-nums',
          muted && 'text-muted-foreground',
        )}
      >
        {value}
      </dd>
      {detail !== undefined && <dd className="m-0 text-[13px] text-muted-foreground">{detail}</dd>}
    </div>
  );
}

function Figures({ customer }: { customer: CustomerProfile }) {
  const titleId = useId();
  const none = customer.purchases === 0;
  const { className, style } = rise(0);
  return (
    <Card aria-labelledby={titleId} className={className} style={style}>
      <CardHeader>
        <CardTitle id={titleId}>{messages.figures}</CardTitle>
      </CardHeader>
      <CardContent>
        {/* Side by side only where each figure has room for a whole amount; never split a number. */}
        <dl className="m-0 grid grid-cols-[repeat(auto-fit,minmax(11rem,1fr))] gap-4">
          <Figure
            label={messages.purchases}
            value={customer.purchases.toLocaleString('es-AR')}
            detail={messages.ofOrders(customer.orderCount)}
            muted={none}
          />
          <Figure label={messages.spent} value={formatMoney(customer.spentCents)} muted={none} />
          <Figure
            label={messages.lastPurchase}
            value={customer.lastPurchaseAt === null ? messages.noPurchases : formatDate(customer.lastPurchaseAt)}
            muted={none}
          />
        </dl>
        <p className="m-0 text-[13px] text-muted-foreground">{messages.purchasesHint}</p>
      </CardContent>
    </Card>
  );
}

function Orders({
  customer,
  orders,
  orderState,
  children,
}: {
  customer: CustomerProfile;
  orders: CustomerOrder[];
  orderState: ProfileReturn;
  children: ReactNode;
}) {
  const titleId = useId();
  const { className, style } = rise(1);
  return (
    <section aria-labelledby={titleId} className={cn('flex min-w-0 flex-col gap-3', className)} style={style}>
      <div className="flex items-center gap-2">
        <h2 id={titleId} className="m-0 text-base font-bold">
          {messages.orders}
        </h2>
        <Badge>{customer.orderCount.toLocaleString('es-AR')}</Badge>
      </div>
      {orders.length === 0 ? (
        <EmptyState title={messages.noOrders}>{messages.noOrdersHint}</EmptyState>
      ) : (
        <ul className="admin-list">
          {orders.map((order) => (
            <li key={order.id} className="admin-card">
              <div className="admin-card-head">
                <Link to={`/admin/pedidos/${order.id}`} state={orderState} className="admin-card-title">
                  {esAdmin.orderList.number(order.number)}
                </Link>
                <span className="admin-badge">{es.tracking.statuses[order.status]}</span>
                <span className="order-total">{formatMoney(order.totalCents)}</span>
              </div>
              <p className="admin-card-meta">{formatDateTime(order.createdAt)}</p>
            </li>
          ))}
        </ul>
      )}
      {children}
    </section>
  );
}

/** How long "Copiado" stays before the button offers "Copiar" again. */
const COPIED_MS = 2000;

/**
 * "Copiar", then "Copiado" for a moment once the text is on the clipboard. The status region sits next to the button,
 * not inside it, so screen readers announce it; it empties again, so a second copy is announced too.
 */
function CopyButton({ text, label }: { text: string; label: string }) {
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setCopied(false);
    }, COPIED_MS);
    return () => {
      window.clearTimeout(timer);
    };
  }, [copied]);

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      // Without clipboard access the text is still on screen to copy by hand.
    }
  }

  return (
    <>
      <Button variant="ghost" size="sm" aria-label={label} onClick={() => void copy()}>
        {copied ? <Check aria-hidden="true" className="text-success" /> : <Copy aria-hidden="true" />}
        <span aria-hidden="true">{copied ? messages.copied : messages.copy}</span>
      </Button>
      <span role="status" className="sr-only">
        {copied ? messages.copied : ''}
      </span>
    </>
  );
}

function Contact({ customer }: { customer: CustomerProfile }) {
  const titleId = useId();
  const whatsApp = customer.phone === null ? null : whatsAppLink(customer.phone);
  const { className, style } = rise(2);
  return (
    <Card aria-labelledby={titleId} className={className} style={style}>
      <CardHeader>
        <CardTitle id={titleId}>{messages.contact}</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="m-0 flex flex-col gap-4">
          <div className="flex min-w-0 flex-col gap-1.5">
            <dt className="flex items-center gap-2 text-[13px] text-muted-foreground">
              {messages.email}
              <Badge tone={customer.emailVerified ? 'success' : 'warning'}>
                {customer.emailVerified ? messages.verified : messages.unverified}
              </Badge>
            </dt>
            <dd className="m-0 font-semibold [overflow-wrap:anywhere]">{customer.email}</dd>
            <dd className="m-0 flex flex-wrap gap-2">
              <Button asChild size="sm">
                <a href={mailLink(customer.email)}>
                  <Mail aria-hidden="true" />
                  {messages.writeEmail}
                </a>
              </Button>
              <CopyButton text={customer.email} label={messages.copyEmail} />
            </dd>
          </div>
          <div className="flex min-w-0 flex-col gap-1.5">
            <dt className="text-[13px] text-muted-foreground">{messages.phone}</dt>
            {customer.phone === null ? (
              <dd className="m-0 text-muted-foreground">{messages.noPhone}</dd>
            ) : (
              <>
                <dd className="m-0 font-semibold tabular-nums">{customer.phone}</dd>
                <dd className="m-0 flex flex-wrap gap-2">
                  {whatsApp !== null && (
                    <Button asChild size="sm">
                      <a href={whatsApp} target="_blank" rel="noopener noreferrer">
                        <MessageCircle aria-hidden="true" />
                        {messages.openWhatsApp}
                      </a>
                    </Button>
                  )}
                  <CopyButton text={customer.phone} label={messages.copyPhone} />
                </dd>
              </>
            )}
          </div>
          <div className="flex min-w-0 flex-col gap-1.5">
            <dt className="text-[13px] text-muted-foreground">{messages.signIn}</dt>
            <dd className="m-0 flex flex-wrap gap-2">
              {customer.providers.length === 0 ? (
                <span>{messages.noProviders}</span>
              ) : (
                customer.providers.map((provider) => <Badge key={provider}>{messages.providers[provider]}</Badge>)
              )}
            </dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  );
}

function AddressLines({ address }: { address: CustomerAddress }) {
  const floorAndApartment = esAdmin.orderDetail.floorAndApartment(address.floor, address.apartment);
  const province = PROVINCES[address.province] ?? address.province;
  return (
    <>
      <span className="font-semibold">
        {`${address.street} ${address.number}`}
        {floorAndApartment !== '' && `, ${floorAndApartment}`}
      </span>
      <span className="text-[13px] text-muted-foreground">
        {messages.cityLine(address.city, province, address.postalCode)}
      </span>
    </>
  );
}

function Addresses({ addresses }: { addresses: CustomerAddress[] }) {
  const titleId = useId();
  const { className, style } = rise(3);
  return (
    <Card aria-labelledby={titleId} className={className} style={style}>
      <CardHeader>
        <CardTitle id={titleId}>{messages.addresses}</CardTitle>
      </CardHeader>
      <CardContent>
        {addresses.length === 0 ? (
          <p className="m-0 text-muted-foreground">{messages.noAddresses}</p>
        ) : (
          <ul className="m-0 flex list-none flex-col p-0">
            {addresses.map((address) => (
              <li
                key={address.id}
                className="flex flex-col gap-0.5 border-b border-border py-2.5 first:pt-0 last:border-b-0 last:pb-0 [overflow-wrap:anywhere]"
              >
                {address.primary && (
                  <Badge tone="accent" className="mb-1 self-start">
                    {messages.primary}
                  </Badge>
                )}
                <AddressLines address={address} />
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
