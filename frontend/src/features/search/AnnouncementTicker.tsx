import { useLayoutEffect, useRef, useState, type CSSProperties } from 'react';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { usePaymentProviders } from '../checkout/useCheckout.ts';
import { paymentMethodNames } from '../help/paymentMethods.ts';
import { useShippingRates } from '../help/useShippingRates.ts';

const messages = es.header.ticker;

/** How fast the messages travel, in pixels per second. */
const SPEED = 40;

/**
 * The store's messages (UC-31, T-E7-39): "Envío gratis desde $X" with a threshold, the carrier, and the enabled payment
 * providers once they are known. No installments or discounts (D-01, D-02). The carrier is always there, so the
 * ticker never appears late and pushes the header down.
 */
function useMessages(): string[] {
  const threshold = useShippingRates().data?.freeShippingThresholdCents ?? null;
  const providers = usePaymentProviders().data ?? [];
  return [
    ...(threshold === null ? [] : [es.header.freeShippingFrom(formatMoney(threshold))]),
    messages.carrier,
    ...(providers.length > 0 ? [es.faq.summary.payWith(paymentMethodNames(providers))] : []),
  ];
}

/**
 * The announcement ticker above the header (UC-31, T-E7-39): the messages run from right to left in a loop. The track
 * holds the list twice, each copy at least as wide as the screen, and slides by one copy, so the loop has no seam;
 * screen readers read only the first copy. It stops under the pointer, with the keyboard focus inside, or with its
 * pause button (WCAG 2.2.2); under reduced motion it stays still (CSS).
 */
export function AnnouncementTicker() {
  const items = useMessages();
  const [paused, setPaused] = useState(false);
  const copy = useRef<HTMLUListElement>(null);
  // One copy's width at SPEED; until it is measured, the CSS default.
  const [seconds, setSeconds] = useState<number | null>(null);

  useLayoutEffect(() => {
    const element = copy.current;
    if (element === null) {
      return undefined;
    }
    function measure(width: number) {
      setSeconds(width > 0 ? Math.round((width / SPEED) * 10) / 10 : null);
    }
    measure(element.offsetWidth);
    // jsdom has no ResizeObserver; browsers measure again when the screen or the messages change.
    if (typeof ResizeObserver === 'undefined') {
      return undefined;
    }
    const observer = new ResizeObserver(() => {
      measure(element.offsetWidth);
    });
    observer.observe(element);
    return () => {
      observer.disconnect();
    };
  }, []);

  const list = items.map((item) => <li key={item}>{item}</li>);
  const style = seconds === null ? undefined : ({ '--ticker-duration': `${String(seconds)}s` } as CSSProperties);
  return (
    <section className="announce" aria-label={messages.label} data-paused={paused || undefined} style={style}>
      <div className="announce-viewport">
        <div className="announce-track">
          <ul ref={copy} className="announce-items">
            {list}
          </ul>
          <ul className="announce-items" aria-hidden="true">
            {list}
          </ul>
        </div>
      </div>
      <button
        type="button"
        className="announce-toggle"
        aria-label={paused ? messages.resume : messages.pause}
        onClick={() => {
          setPaused(!paused);
        }}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          {paused ? <path d="M8 5.5v13l10.5-6.5z" /> : <path d="M8 5.5v13M16 5.5v13" />}
        </svg>
      </button>
    </section>
  );
}
