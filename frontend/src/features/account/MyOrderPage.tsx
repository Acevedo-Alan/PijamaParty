import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Navigate, useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import { isPaid } from '../orders/OrderConfirmation.tsx';
import type { OrderStatus } from '../orders/orderTrackingApi.ts';
import { OrderItemsAndAmounts } from '../orders/OrderItemsAndAmounts.tsx';
import { ORDERS_PATH, SIGN_IN_PATH } from './AccountLink.tsx';
import { Group } from './Group.tsx';
import { CORREO_TRACKING_URL, fetchMyOrder, myOrderKey, type MyOrder, type MyOrderAddress } from './myOrdersApi.ts';
import { OrderCard, stepOf } from './OrderCard.tsx';
import { useCustomer } from './useCustomer.ts';
import './account.css';

const messages = es.account.orders;

/** Paid and not yet shipped: the tracking number is still to come. */
const BEFORE_SHIPPING: ReadonlySet<OrderStatus> = new Set(['PAID', 'PREPARING']);

/** "San Martín 1234, piso 3, depto. B". */
function streetLine(address: MyOrderAddress): string {
  const parts = [`${address.street} ${address.number}`];
  if (address.floor !== null) {
    parts.push(es.checkout.floorPart(address.floor));
  }
  if (address.apartment !== null) {
    parts.push(es.checkout.apartmentPart(address.apartment));
  }
  return parts.join(', ');
}

/**
 * "Envío" (T-E7-64): the status, the bar of a paid order and, once shipped, the tracking number with "Copiar" and
 * Correo Argentino's page, which does not take the number in its link.
 */
function Shipment({ order }: { order: MyOrder }) {
  const [copied, setCopied] = useState(false);
  const number = order.shipmentTrackingNumber;
  const { status } = order;

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      // Without clipboard access the number is still on screen to copy by hand.
    }
  }

  return (
    <Group.Section title={messages.shipment}>
      <Group.Card>
        <div className="group-row order-card-body">
          <p className="order-card-status">
            <OrderCard.StatusDot status={status} />
            {es.tracking.statuses[status]}
          </p>
          {isPaid(status) && (
            <OrderCard.Progress
              status={status}
              caption={status === 'DELIVERED' ? undefined : messages.stepWithCarrier(stepOf(status))}
            />
          )}
          {number === null && BEFORE_SHIPPING.has(status) && (
            <p className="order-card-note">{messages.noShipmentYet}</p>
          )}
        </div>
        {number !== null && (
          <>
            <Group.Field label={messages.trackingNumber}>
              <span className="my-order-tracking-number">
                <span>{number}</span>
                <button type="button" className="group-inline-button" onClick={() => void copy(number)}>
                  {copied ? messages.copied : messages.copy}
                </button>
              </span>
            </Group.Field>
            <Group.ActionLink to={CORREO_TRACKING_URL} target="_blank" rel="noopener noreferrer" icon="external">
              {messages.trackAtCorreo}
            </Group.ActionLink>
          </>
        )}
      </Group.Card>
      {number !== null && <Group.Footnote>{messages.trackingHint}</Group.Footnote>}
    </Group.Section>
  );
}

function MyOrderView({ order }: { order: MyOrder }) {
  const { address } = order;
  return (
    <>
      <title>{`${es.tracking.title(order.number)} | ${es.store.name}`}</title>
      <h1>{es.tracking.title(order.number)}</h1>
      <p className="my-order-date">{messages.placedOn(formatDate(order.createdAt))}</p>
      <Shipment order={order} />
      <Group.Section title={messages.items}>
        <Group.Card>
          <div className="group-row my-order-items">
            <OrderItemsAndAmounts {...order} />
          </div>
          {/* T-E4-19: once paid; an order still awaiting payment has not been collected by anyone. */}
          {isPaid(order.status) && (
            <p className="group-row my-order-paid-with">{messages.paidWith[order.paymentProvider]}</p>
          )}
        </Group.Card>
      </Group.Section>
      <Group.Section title={messages.address}>
        <Group.Card>
          <div className="group-row my-order-address">
            <p>{streetLine(address)}</p>
            <p>{`${address.city}, ${es.provinces[address.province]} (${address.postalCode})`}</p>
          </div>
        </Group.Card>
      </Group.Section>
    </>
  );
}

/**
 * UC-22 (T-E4-07): one of the signed-in customer's orders at /cuenta/pedidos/:orderId, with its tracking. T-E7-64:
 * "‹ Mis pedidos" at the top (on a phone in place of the account's pills) and grouped sections.
 */
export function MyOrderPage() {
  const { orderId = '' } = useParams();
  const customer = useCustomer();
  const order = useQuery({
    queryKey: myOrderKey(orderId),
    queryFn: ({ signal }) => fetchMyOrder(orderId, signal),
    enabled: customer !== undefined && customer !== null,
  });

  if (customer === null || order.data?.status === 'signedOut') {
    return <Navigate to={SIGN_IN_PATH} replace />;
  }

  return (
    <div className="account-page my-order tracking">
      <Link to={ORDERS_PATH} className="my-order-back" aria-label={messages.back}>
        <span aria-hidden="true">‹</span> {messages.backShort}
      </Link>
      {order.isPending && <p role="status">{messages.loadingOrder}</p>}
      {order.isError && (
        <div className="address-load-error" role="alert">
          <p>{messages.loadOrderError}</p>
          <button type="button" className="btn" onClick={() => void order.refetch()}>
            {messages.retry}
          </button>
        </div>
      )}
      {order.data?.status === 'notFound' && (
        <>
          <h1>{messages.notFoundTitle}</h1>
          <p>{messages.notFoundBody}</p>
        </>
      )}
      {order.data?.status === 'found' && <MyOrderView order={order.data.order} />}
    </div>
  );
}
