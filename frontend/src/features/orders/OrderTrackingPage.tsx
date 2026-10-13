import { Link, useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatTime } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { RetryAnswer, TrackedOrder } from './orderTrackingApi.ts';
import { goToPayment } from './redirect.ts';
import { useRetryPayment, useTrackedOrder } from './useTrackedOrder.ts';
import './orders.css';

/** Why the last tap on "Pagar" did not leave the store; nothing while it works or after a redirect. */
function PayProblem({ answer, failed }: { answer: RetryAnswer | undefined; failed: boolean }) {
  if (failed) {
    return <>{es.tracking.payError}</>;
  }
  if (answer?.status === 'providerUnavailable') {
    return <>{es.tracking.providerUnavailable}</>;
  }
  if (answer?.status === 'tooManyAttempts') {
    return <>{es.tracking.tooManyAttempts}</>;
  }
  return null;
}

/** UC-14: an order awaiting payment can be paid again while its stock is reserved. */
function PaymentPanel({ token, order }: { token: string; order: TrackedOrder }) {
  const retry = useRetryPayment(token);

  function pay() {
    retry.mutate(undefined, {
      onSuccess: (answer) => {
        if (answer.status === 'opened') {
          goToPayment(answer.paymentUrl);
        }
      },
    });
  }

  if (order.status !== 'AWAITING_PAYMENT') {
    return null;
  }
  if (!order.canRetryPayment) {
    return <p className="tracking-note">{es.tracking.reservationOver}</p>;
  }
  return (
    <div className="tracking-pay">
      <p>{es.tracking.reservedUntil(formatTime(order.reservationExpiresAt))}</p>
      <button type="button" className="btn" disabled={retry.isPending} onClick={pay}>
        {retry.isPending ? es.tracking.paying : es.tracking.pay}
      </button>
      <p className="tracking-error" role="alert">
        <PayProblem answer={retry.data} failed={retry.isError} />
      </p>
    </div>
  );
}

function TrackedOrderView({ token, order }: { token: string; order: TrackedOrder }) {
  return (
    <>
      <title>{`${es.tracking.title(order.number)} | ${es.store.name}`}</title>
      <p className="tracking-greeting">{es.tracking.greeting(order.buyerFirstName)}</p>
      <h1>{es.tracking.title(order.number)}</h1>
      <p className={`tracking-status status-${order.status.toLowerCase()}`}>{es.tracking.statuses[order.status]}</p>
      <PaymentPanel token={token} order={order} />
      {order.shipmentTrackingNumber !== null && (
        <dl className="tracking-shipment">
          <dt>{es.tracking.shipment}</dt>
          <dd>{order.shipmentTrackingNumber}</dd>
        </dl>
      )}
      <section className="tracking-summary" aria-labelledby="tracking-items">
        <h2 id="tracking-items">{es.tracking.items}</h2>
        <ul className="tracking-items">
          {order.items.map((item, index) => (
            <li key={index}>
              <span className="tracking-item-name">{item.productName}</span>
              <span className="tracking-item-detail">{es.tracking.itemDetail(item.color, item.size, item.quantity)}</span>
              <span className="tracking-item-amount">{formatMoney(item.lineTotalCents)}</span>
            </li>
          ))}
        </ul>
        <dl className="tracking-amounts">
          <div>
            <dt>{es.tracking.subtotal}</dt>
            <dd>{formatMoney(order.subtotalCents)}</dd>
          </div>
          {order.discountCents > 0 && (
            <div>
              <dt>{order.couponCode === null ? es.tracking.discount : es.tracking.discountWith(order.couponCode)}</dt>
              <dd>−{formatMoney(order.discountCents)}</dd>
            </div>
          )}
          <div>
            <dt>{es.tracking.shipping}</dt>
            <dd>{order.shippingCents === 0 ? es.tracking.free : formatMoney(order.shippingCents)}</dd>
          </div>
          <div className="tracking-total">
            <dt>{es.tracking.total}</dt>
            <dd>{formatMoney(order.totalCents)}</dd>
          </div>
        </dl>
        <p className="tracking-ship-to">{es.tracking.shipTo(order.city, es.provinces[order.province])}</p>
      </section>
    </>
  );
}

/** The tracking link at /pedido/:token (UC-14, T-E3-08): the order, its status and, while pending, paying again. */
export function OrderTrackingPage() {
  const { token = '' } = useParams();
  const tracked = useTrackedOrder(token);

  return (
    <main className="wrap tracking">
      {tracked.isPending && (
        <p className="tracking-note" role="status">
          {es.tracking.loading}
        </p>
      )}
      {tracked.isError && (
        <div className="empty-state" role="alert">
          <p>{es.tracking.loadError}</p>
          <button type="button" className="btn" onClick={() => void tracked.refetch()}>
            {es.tracking.retry}
          </button>
        </div>
      )}
      {tracked.data?.status === 'notFound' && (
        <>
          <h1>{es.tracking.notFoundTitle}</h1>
          <p>{es.tracking.notFoundBody}</p>
          <Link to="/">{es.tracking.backToStore}</Link>
        </>
      )}
      {tracked.data?.status === 'found' && <TrackedOrderView token={token} order={tracked.data.order} />}
    </main>
  );
}
