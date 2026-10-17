import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Navigate, useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import type { OrderStatus } from '../orders/orderTrackingApi.ts';
import { OrderItemsAndAmounts } from '../orders/OrderItemsAndAmounts.tsx';
import { ORDERS_PATH, SIGN_IN_PATH } from './AccountLink.tsx';
import { CORREO_TRACKING_URL, fetchMyOrder, myOrderKey, type MyOrder, type MyOrderAddress } from './myOrdersApi.ts';
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

/** The number, a "Copiar" button and Correo Argentino's tracking page, which does not take the number in its link. */
function Shipment({ order }: { order: MyOrder }) {
  const [copied, setCopied] = useState(false);
  const number = order.shipmentTrackingNumber;

  if (number === null) {
    return BEFORE_SHIPPING.has(order.status) ? <p className="tracking-note">{messages.noShipmentYet}</p> : null;
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      // Without clipboard access the number is still on screen to copy by hand.
    }
  }

  return (
    <section className="my-order-shipment" aria-labelledby="my-order-shipment">
      <h2 id="my-order-shipment">{messages.shipment}</h2>
      <p className="my-order-tracking-number">
        <span>{number}</span>
        <button type="button" className="link-btn" onClick={() => void copy(number)}>
          {copied ? messages.copied : messages.copy}
        </button>
      </p>
      <p>
        <a href={CORREO_TRACKING_URL} target="_blank" rel="noopener noreferrer">
          {messages.trackAtCorreo}
        </a>
      </p>
      <p className="my-order-hint">{messages.trackingHint}</p>
    </section>
  );
}

function MyOrderView({ order }: { order: MyOrder }) {
  return (
    <>
      <title>{`${es.tracking.title(order.number)} | ${es.store.name}`}</title>
      <h1>{es.tracking.title(order.number)}</h1>
      <p className="my-order-date">{messages.placedOn(formatDate(order.createdAt))}</p>
      <p className={`tracking-status status-${order.status.toLowerCase()}`}>{es.tracking.statuses[order.status]}</p>
      <Shipment order={order} />
      <section className="tracking-summary" aria-labelledby="my-order-items">
        <h2 id="my-order-items">{messages.items}</h2>
        <OrderItemsAndAmounts {...order} />
      </section>
      <section className="my-order-address" aria-labelledby="my-order-address">
        <h2 id="my-order-address">{messages.address}</h2>
        <p>{streetLine(order.address)}</p>
        <p>{`${order.address.postalCode} ${order.address.city}, ${es.provinces[order.address.province]}`}</p>
      </section>
    </>
  );
}

/** UC-22 (T-E4-07): one of the signed-in customer's orders at /cuenta/pedidos/:orderId, with its tracking. */
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
    <main className="wrap account-page my-order tracking">
      <Link to={ORDERS_PATH} className="my-order-back">
        {messages.back}
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
    </main>
  );
}
