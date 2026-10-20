import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { usePaymentProviders } from '../checkout/useCheckout.ts';
import { FAQ_PATH } from './FaqLink.tsx';
import { paymentMethodNames } from './paymentMethods.ts';
import { useShippingRates } from './useShippingRates.ts';
import './help.css';

const messages = es.faq.summary;

function Badge({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <li className="trust-badge">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        {icon}
      </svg>
      <span>{children}</span>
    </li>
  );
}

/**
 * RNF-31.6 (T-E5-12) as the trust icons of E7 (D-12), below the add to cart: the free-shipping threshold (when there
 * is one), the delivery times and the payment methods enabled now, with a link to the FAQ. What could not be read is
 * left out.
 */
export function PurchaseInfoSummary() {
  const threshold = useShippingRates().data?.freeShippingThresholdCents ?? null;
  const providers = usePaymentProviders().data ?? [];
  return (
    <section className="purchase-info" aria-label={messages.label}>
      <ul className="trust-badges">
        {threshold !== null && (
          <Badge
            icon={
              <>
                <path d="M3 6h11v11H3zM14 10h4l3 3v4h-7z" />
                <circle cx="7.5" cy="18" r="1.5" />
                <circle cx="17.5" cy="18" r="1.5" />
              </>
            }
          >
            {messages.freeFrom(formatMoney(threshold))}
          </Badge>
        )}
        <Badge
          icon={
            <>
              <circle cx="12" cy="12" r="8.5" />
              <path d="M12 7.5V12l3 2" />
            </>
          }
        >
          {messages.delivery}
        </Badge>
        {providers.length > 0 && (
          <Badge
            icon={
              <>
                <path d="M12 3 20 6v5c0 5-3.4 8.2-8 10-4.6-1.8-8-5-8-10V6z" />
                <path d="m9 12 2 2 4-4" />
              </>
            }
          >
            {messages.payWith(paymentMethodNames(providers))}
          </Badge>
        )}
      </ul>
      <Link to={FAQ_PATH}>{messages.link}</Link>
    </section>
  );
}
