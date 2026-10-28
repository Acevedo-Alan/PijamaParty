import { useEffect, useId, useState, type SyntheticEvent } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { AlertBox } from '../../components/AlertBox.tsx';
import { capitalizeWords } from '../../i18n/capitalizeWords.ts';
import { es } from '../../i18n/es.ts';
import { formatTime } from '../../i18n/formatDate.ts';
import { trackPurchase } from '../analytics/checkoutEvents.ts';
import { OrderItemsAndAmounts } from './OrderItemsAndAmounts.tsx';
import type { EmailCorrectionAnswer, OrderStatus, RetryAnswer, TrackedOrder } from './orderTrackingApi.ts';
import { goToPayment } from './redirect.ts';
import { useCorrectEmail, useRetryPayment, useTrackedOrder } from './useTrackedOrder.ts';
import './orders.css';

/** Why the last tap on "Pagar" did not leave the store; nothing while it works or after a redirect. */
function payProblem(answer: RetryAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return es.tracking.payError;
  }
  if (answer?.status === 'providerUnavailable') {
    return es.tracking.providerUnavailable;
  }
  if (answer?.status === 'tooManyAttempts') {
    return es.tracking.tooManyAttempts;
  }
  return null;
}

/** What the return from the payment provider tells the tracking page (T-E3-11), in the router state. */
export interface TrackingState {
  paymentApproved: boolean;
}

/** How long the page waits for the webhook after an approved return before showing "Pagar" again. */
export const CONFIRMING_FOR_MS = 120_000;

function approvedReturn(state: unknown): boolean {
  return typeof state === 'object' && state !== null && (state as Partial<TrackingState>).paymentApproved === true;
}

/** True from an approved return until 2 minutes later; a reload drops the router state and with it this. */
function useConfirmingPayment(): boolean {
  const approved = approvedReturn(useLocation().state);
  const [confirming, setConfirming] = useState(approved);
  useEffect(() => {
    if (!approved) {
      return;
    }
    const timer = setTimeout(() => {
      setConfirming(false);
    }, CONFIRMING_FOR_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [approved]);
  return confirming;
}

/**
 * UC-14: an order awaiting payment can be paid again while its stock is reserved; right after an approved return it
 * says the payment is being confirmed instead.
 */
function PaymentPanel({ token, order, confirming }: { token: string; order: TrackedOrder; confirming: boolean }) {
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
  if (confirming) {
    return (
      <p className="tracking-note tracking-confirming" role="status">
        {es.tracking.confirmingPayment}
      </p>
    );
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
      <AlertBox message={payProblem(retry.data, retry.isError)} />
    </div>
  );
}

/** Why the email was not saved; nothing while it works, after it is saved or when the order is read again. */
function emailProblem(answer: EmailCorrectionAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return es.tracking.emailCorrection.error;
  }
  if (answer?.status === 'invalidEmail') {
    return es.tracking.emailCorrection.invalid;
  }
  if (answer?.status === 'tooManyAttempts') {
    return es.tracking.emailCorrection.tooManyAttempts;
  }
  return null;
}

/** The statuses that close an order: its email is no longer corrected (UC-17). */
const CLOSED: ReadonlySet<OrderStatus> = new Set(['CANCELLED', 'EXPIRED']);

/**
 * UC-17 (T-E3-23): the buyer corrects a misspelled email. The page never shows the order's email, since the link can
 * be forwarded, so the field starts empty; on a paid order the confirmation is sent again to the new one.
 */
function EmailCorrection({ token, order }: { token: string; order: TrackedOrder }) {
  const correction = useCorrectEmail(token);
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState('');
  const [saved, setSaved] = useState<{ email: string; resent: boolean } | null>(null);
  const id = useId();

  function save(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const typed = email.trim();
    correction.mutate(typed, {
      onSuccess: (answer) => {
        if (answer.status === 'corrected') {
          setSaved({ email: typed, resent: answer.confirmationResent });
          setOpen(false);
          setEmail('');
        }
      },
    });
  }

  if (CLOSED.has(order.status)) {
    return null;
  }
  return (
    <section className="tracking-email">
      <p className="tracking-email-saved" role="status">
        {saved !== null &&
          (saved.resent
            ? es.tracking.emailCorrection.resent(saved.email)
            : es.tracking.emailCorrection.savedForLater(saved.email))}
      </p>
      {open ? (
        <form className="tracking-email-form" onSubmit={save} noValidate>
          <label htmlFor={id}>{es.tracking.emailCorrection.label}</label>
          <input
            id={id}
            name="email"
            type="email"
            inputMode="email"
            autoComplete="email"
            maxLength={254}
            required
            value={email}
            onChange={(event) => {
              setEmail(event.target.value);
            }}
          />
          <button type="submit" className="btn" disabled={correction.isPending || email.trim() === ''}>
            {correction.isPending ? es.tracking.emailCorrection.saving : es.tracking.emailCorrection.save}
          </button>
          <AlertBox message={emailProblem(correction.data, correction.isError)} />
        </form>
      ) : (
        <button
          type="button"
          className="link-btn"
          onClick={() => {
            setOpen(true);
            setSaved(null);
            correction.reset();
          }}
        >
          {es.tracking.emailCorrection.open}
        </button>
      )}
    </section>
  );
}

/** The statuses of an order already paid (UC-14): its purchase counts from PAID on, never if it was not paid. */
const PAID: ReadonlySet<OrderStatus> = new Set(['PAID', 'PREPARING', 'SHIPPED', 'DELIVERED']);

/** RNF-25: purchase once the order is paid, also when it becomes paid while the page polls. */
function usePurchaseEvent(order: TrackedOrder) {
  const paid = PAID.has(order.status);
  useEffect(() => {
    if (paid) {
      trackPurchase({
        number: order.number,
        items: order.items.map(({ sku, productName, size, color, unitPriceCents, quantity }) => ({
          sku,
          name: productName,
          size,
          color,
          unitPriceCents,
          quantity,
        })),
        totalCents: order.totalCents,
        shippingCents: order.shippingCents,
        couponCode: order.couponCode,
      });
    }
    // Once per order: the mark in trackPurchase, not these dependencies, keeps it from repeating.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [order.number, paid]);
}

/**
 * One column on a phone; from 900 px the order and what can be done with it on the left, the summary on the right
 * (E7). The name and the city show capitalized, as the buyer may have typed them in lowercase.
 */
function TrackedOrderView({ token, order, confirming }: { token: string; order: TrackedOrder; confirming: boolean }) {
  usePurchaseEvent(order);
  return (
    <div className="tracking-layout">
      <title>{`${es.tracking.title(order.number)} | ${es.store.name}`}</title>
      <div className="tracking-main">
        <p className="tracking-greeting">{es.tracking.greeting(capitalizeWords(order.buyerFirstName))}</p>
        <h1>{es.tracking.title(order.number)}</h1>
        <p className={`tracking-status status-${order.status.toLowerCase()}`}>{es.tracking.statuses[order.status]}</p>
        <PaymentPanel token={token} order={order} confirming={confirming} />
        {order.shipmentTrackingNumber !== null && (
          <dl className="tracking-shipment">
            <dt>{es.tracking.shipment}</dt>
            <dd>{order.shipmentTrackingNumber}</dd>
          </dl>
        )}
      </div>
      <section className="tracking-summary" aria-labelledby="tracking-items">
        <h2 id="tracking-items">{es.tracking.items}</h2>
        <OrderItemsAndAmounts {...order} />
        <p className="tracking-ship-to">
          {es.tracking.shipTo(capitalizeWords(order.city), es.provinces[order.province])}
        </p>
      </section>
      <EmailCorrection token={token} order={order} />
    </div>
  );
}

/** The tracking link at /pedido/:token (UC-14, T-E3-08): the order, its status and, while pending, paying again. */
export function OrderTrackingPage() {
  const { token = '' } = useParams();
  const confirming = useConfirmingPayment();
  const tracked = useTrackedOrder(token, confirming);

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
      {tracked.data?.status === 'found' && (
        <TrackedOrderView token={token} order={tracked.data.order} confirming={confirming} />
      )}
    </main>
  );
}
