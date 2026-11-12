import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { Chibi } from '../../components/chibi/Chibi.tsx';
import { capitalizeWords } from '../../i18n/capitalizeWords.ts';
import { es } from '../../i18n/es.ts';
import { whatsAppUrl } from '../help/storeContact.ts';
import { useStoreContact } from '../help/useStoreContact.ts';
import type { OrderStatus, TrackedOrder } from './orderTrackingApi.ts';

/** The states of an order already paid (UC-14, GLOSSARY.md), in the order the order goes through them. */
export const PAID_STEPS = ['PAID', 'PREPARING', 'SHIPPED', 'DELIVERED'] as const satisfies readonly OrderStatus[];

export type PaidStatus = (typeof PAID_STEPS)[number];

/** Paid from PAID on; only the webhook sets it, never the return from the provider (UC-15). */
export function isPaid(status: OrderStatus): status is PaidStatus {
  return (PAID_STEPS as readonly OrderStatus[]).includes(status);
}

/** How long "Copiado" stays on the button. */
const COPIED_FOR_MS = 2000;

/** "Copiar" next to the order number: only where the browser lets the page write to the clipboard. */
function CopyNumber({ number }: { number: number }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle');
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(
    () => () => {
      clearTimeout(timer.current);
    },
    [],
  );

  // The types promise a clipboard, but it is missing outside secure contexts and in some browsers.
  const clipboard = (navigator as Partial<Navigator>).clipboard;
  if (clipboard === undefined) {
    return null;
  }

  function copy() {
    if (clipboard === undefined) {
      return;
    }
    clearTimeout(timer.current);
    clipboard.writeText(`#${String(number)}`).then(
      () => {
        setState('copied');
        timer.current = setTimeout(() => {
          setState('idle');
        }, COPIED_FOR_MS);
      },
      () => {
        setState('failed');
      },
    );
  }

  return (
    <>
      <button
        type="button"
        className="tracking-copy"
        aria-label={state === 'copied' ? es.tracking.paid.copied : es.tracking.paid.copyLabel(number)}
        onClick={copy}
      >
        {state === 'copied' ? es.tracking.paid.copied : es.tracking.paid.copy}
      </button>
      <span className="visually-hidden" role="status">
        {state === 'copied' ? es.tracking.paid.copied : state === 'failed' ? es.tracking.paid.copyFailed : ''}
      </span>
    </>
  );
}

/**
 * T-E7-44: a paid order thanks the buyer; the line under the heading follows the order's state. T-E7-48: the store's
 * chibi celebrates it, with the check as a seal at its side; the check draws itself once (CSS, not under reduced
 * motion). Both are decorative: the heading says it.
 */
export function PaidHeader({ order, status }: { order: TrackedOrder; status: PaidStatus }) {
  return (
    <header className="tracking-paid">
      <div className="tracking-paid-chibi">
        <Chibi mood="success" pose="paid" />
        <svg className="tracking-paid-check" viewBox="0 0 48 48" aria-hidden="true">
          <circle cx="24" cy="24" r="24" />
          <path d="M14 24.5l7 7 13-14" pathLength={1} />
        </svg>
      </div>
      <h1 data-kaomoji={es.kaomoji.paid}>{es.tracking.paid.thanks(capitalizeWords(order.buyerFirstName))}</h1>
      <p className="tracking-paid-line">{es.tracking.paid.lines[status]}</p>
      <p className="tracking-number">
        <span className="tracking-number-badge">{es.tracking.title(order.number)}</span>
        <CopyNumber number={order.number} />
      </p>
    </header>
  );
}

/** T-E7-44: the four paid states as steps, drawn like the checkout's (T-E7-38). */
export function OrderProgress({ status }: { status: PaidStatus }) {
  const current = PAID_STEPS.indexOf(status);
  return (
    <ol className="tracking-progress" aria-label={es.tracking.paid.progress}>
      {PAID_STEPS.map((step, index) => {
        const state = index < current ? 'done' : index === current ? 'current' : 'later';
        return (
          <li
            key={step}
            className={`tracking-step is-${state}${state === 'current' && index < PAID_STEPS.length - 1 ? ' is-pulsing' : ''}`}
            aria-current={state === 'current' ? 'step' : undefined}
          >
            <span className="tracking-step-mark" aria-hidden="true">
              {state === 'done' ? (
                <svg viewBox="0 0 16 16">
                  <path d="M3.5 8.5l3 3 6-7" />
                </svg>
              ) : (
                index + 1
              )}
            </span>
            <span className="tracking-step-name">{es.tracking.statuses[step]}</span>
            {state === 'done' && <span className="visually-hidden">{` ${es.tracking.paid.completed}`}</span>}
          </li>
        );
      })}
    </ol>
  );
}

/** T-E7-44: where the order goes (city and province only, UC-14) and, until it ships, the FAQ's delivery time. */
export function DeliveryBlock({ order, status }: { order: TrackedOrder; status: PaidStatus }) {
  const waiting = status === 'PAID' || status === 'PREPARING';
  return (
    <section className="tracking-delivery" aria-labelledby="tracking-delivery">
      <h2 id="tracking-delivery">{es.tracking.paid.delivery}</h2>
      <p>{es.tracking.paid.shipsTo(capitalizeWords(order.city), es.provinces[order.province])}</p>
      {waiting && (
        <p className="tracking-note">
          {order.province === 'SANTA_FE' ? es.tracking.paid.arrivesInSantaFe : es.tracking.paid.arrivesElsewhere}
        </p>
      )}
      <p className="tracking-note">{es.tracking.paid.emailSent}</p>
    </section>
  );
}

/** T-E7-44: the chat with the store, its message carrying the order number and nothing about the buyer. */
export function OrderWhatsApp({ number }: { number: number }) {
  const whatsApp = useStoreContact().whatsAppNumber;
  if (whatsApp === undefined) {
    return null;
  }
  return (
    <a
      className="tracking-whatsapp"
      href={`${whatsAppUrl(whatsApp)}?text=${encodeURIComponent(es.tracking.whatsAppMessage(number))}`}
      target="_blank"
      rel="noopener noreferrer"
    >
      {es.tracking.whatsApp}
    </a>
  );
}

/** T-E7-44: back to the store and, with the store's link (UC-36), its Instagram. */
export function OrderClosing() {
  const instagram = useStoreContact().instagramUrl;
  return (
    <nav className="tracking-closing" aria-label={es.tracking.backToStore}>
      <Link to="/" className="btn">
        {es.tracking.backToStore}
      </Link>
      {instagram !== undefined && (
        <a href={instagram} target="_blank" rel="noopener noreferrer">
          {es.tracking.instagram}
        </a>
      )}
    </nav>
  );
}
