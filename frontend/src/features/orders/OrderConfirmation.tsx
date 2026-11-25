import { type ReactNode, useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { Chibi } from "../../components/chibi/Chibi.tsx";
import { capitalizeWords } from "../../i18n/capitalizeWords.ts";
import { es } from "../../i18n/es.ts";
import { whatsAppUrl } from "../help/storeContact.ts";
import { useStoreContact } from "../help/useStoreContact.ts";
import type { OrderStatus, TrackedOrder } from "./orderTrackingApi.ts";
import { PaidCelebration } from "./PaidCelebration.tsx";

/** The states of an order already paid (UC-14, GLOSSARY.md), in the order the order goes through them. */
export const PAID_STEPS = [
  "PAID",
  "PREPARING",
  "SHIPPED",
  "DELIVERED",
] as const satisfies readonly OrderStatus[];

export type PaidStatus = (typeof PAID_STEPS)[number];

/** Paid from PAID on; only the webhook sets it, never the return from the provider (UC-15). */
export function isPaid(status: OrderStatus): status is PaidStatus {
  return (PAID_STEPS as readonly OrderStatus[]).includes(status);
}

/** How long "Copiado" stays on the button. */
const COPIED_FOR_MS = 2000;

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg
      className="tracking-icon"
      viewBox="0 0 24 24"
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  );
}

/** T-E7-73: line drawings in the account's style (accountIcons.tsx); decorative, the text next to each names it. */
const ICONS = {
  copy: (
    <Icon>
      <rect x="8.5" y="8.5" width="11" height="11" rx="2" />
      <path d="M15.5 8.5V6.5a2 2 0 0 0-2-2h-7a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h2" />
    </Icon>
  ),
  check: (
    <Icon>
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </Icon>
  ),
  truck: (
    <Icon>
      <path d="M3 6.5h11v9H3zM14 9.5h4l3 3v3h-7M7.5 18.5a1.8 1.8 0 1 0 0-.1M17.5 18.5a1.8 1.8 0 1 0 0-.1" />
    </Icon>
  ),
  clock: (
    <Icon>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </Icon>
  ),
  mail: (
    <Icon>
      <rect x="3.5" y="5.5" width="17" height="13" rx="2" />
      <path d="m4 7 8 6 8-6" />
    </Icon>
  ),
};

/** The copy icon inside the order number's pill: only where the browser lets the page write to the clipboard. */
function CopyNumber({ number }: { number: number }) {
  const [state, setState] = useState<"idle" | "copied" | "failed">("idle");
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
        setState("copied");
        timer.current = setTimeout(() => {
          setState("idle");
        }, COPIED_FOR_MS);
      },
      () => {
        setState("failed");
      },
    );
  }

  return (
    <>
      <button
        type="button"
        className={`tracking-copy${state === "copied" ? " is-copied" : ""}`}
        aria-label={
          state === "copied"
            ? es.tracking.paid.copied
            : es.tracking.paid.copyLabel(number)
        }
        onClick={copy}
      >
        {state === "copied" ? (
          <>
            {ICONS.check}
            <span aria-hidden="true">{es.tracking.paid.copied}</span>
          </>
        ) : (
          ICONS.copy
        )}
      </button>
      <span className="visually-hidden" role="status">
        {state === "copied"
          ? es.tracking.paid.copied
          : state === "failed"
            ? es.tracking.paid.copyFailed
            : ""}
      </span>
    </>
  );
}

/**
 * T-E7-44: a paid order thanks the buyer; the line under the heading follows the order's state. T-E7-48: the store's
 * chibi celebrates it, with the check as a seal at its side; the check draws itself once (CSS, not under reduced
 * motion). Both are decorative: the heading says it. T-E7-78: the first time this browser sees the order in PAID, stars
 * and moons burst from behind the chibi.
 */
export function PaidHeader({
  order,
  status,
}: {
  order: TrackedOrder;
  status: PaidStatus;
}) {
  return (
    <header className="tracking-paid">
      <div className="tracking-paid-chibi">
        {status === "PAID" && <PaidCelebration number={order.number} />}
        <Chibi mood="success" pose="paid" />
        <svg
          className="tracking-paid-check"
          viewBox="0 0 48 48"
          aria-hidden="true"
        >
          <circle cx="24" cy="24" r="24" />
          <path d="M14 24.5l7 7 13-14" pathLength={1} />
        </svg>
      </div>
      <h1 data-kaomoji={es.kaomoji.paid}>
        {es.tracking.paid.thanks(capitalizeWords(order.buyerFirstName))}
      </h1>
      <p className="tracking-paid-line">{es.tracking.paid.lines[status]}</p>
      <p className="tracking-number">
        <span className="tracking-number-text">
          {es.tracking.title(order.number)}
        </span>
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
        const state =
          index < current ? "done" : index === current ? "current" : "later";
        return (
          <li
            key={step}
            className={`tracking-step is-${state}${state === "current" && index < PAID_STEPS.length - 1 ? " is-pulsing" : ""}`}
            aria-current={state === "current" ? "step" : undefined}
          >
            <span className="tracking-step-mark" aria-hidden="true">
              {state === "done" ? (
                <svg viewBox="0 0 16 16">
                  <path d="M3.5 8.5l3 3 6-7" />
                </svg>
              ) : (
                index + 1
              )}
            </span>
            <span className="tracking-step-name">
              {es.tracking.statuses[step]}
            </span>
            {state === "done" && (
              <span className="visually-hidden">{` ${es.tracking.paid.completed}`}</span>
            )}
          </li>
        );
      })}
    </ol>
  );
}

/**
 * T-E7-44: where the order goes (city and province only, UC-14) and, until it ships, the FAQ's delivery time.
 * T-E7-73: one line each, after its icon, the carrier, the place and the days in bold.
 */
export function DeliveryBlock({
  order,
  status,
}: {
  order: TrackedOrder;
  status: PaidStatus;
}) {
  const waiting = status === "PAID" || status === "PREPARING";
  const text = es.tracking.paid;
  return (
    <section className="tracking-delivery" aria-labelledby="tracking-delivery">
      <h2 id="tracking-delivery">{text.delivery}</h2>
      <ul className="tracking-delivery-lines">
        <li>
          {ICONS.truck}
          <span>
            <strong>{text.carrier}</strong> {text.shipsHomeIn}{" "}
            <strong>{`${capitalizeWords(order.city)}, ${es.provinces[order.province]}`}</strong>
          </span>
        </li>
        {waiting && (
          <li>
            {ICONS.clock}
            <span>
              {text.arrivesIn}{" "}
              <strong>
                {order.province === "SANTA_FE"
                  ? text.daysInSantaFe
                  : text.daysElsewhere}
              </strong>{" "}
              {text.arrivesSince}
            </span>
          </li>
        )}
        <li className="tracking-note">
          {ICONS.mail}
          <span>{text.emailSent}</span>
        </li>
      </ul>
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
