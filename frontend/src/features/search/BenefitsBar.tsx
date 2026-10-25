import type { ReactNode } from 'react';
import { es } from '../../i18n/es.ts';
import { usePaymentProviders } from '../checkout/useCheckout.ts';
import { paymentMethodNames } from '../help/paymentMethods.ts';
import './footer.css';

const messages = es.benefits;

function Benefit({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <li className="benefit">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        {icon}
      </svg>
      <div>
        <p className="benefit-title">{title}</p>
        <p>{children}</p>
      </div>
    </li>
  );
}

/**
 * The benefits bar right above the footer of every public page (UC-31, T-E7-21): shipping, the payment methods enabled
 * now (the server's list, never a promise the checkout does not keep) and secure buying. While the providers are
 * unknown, or when they cannot be read, the payments block is left out.
 */
export function BenefitsBar() {
  const providers = usePaymentProviders().data ?? [];
  return (
    <section className="benefits" aria-label={messages.label}>
      <ul className="wrap benefits-list">
        <Benefit
          title={messages.shippingTitle}
          icon={
            <>
              <path d="M3 6h11v11H3zM14 10h4l3 3v4h-7z" />
              <circle cx="7.5" cy="18" r="1.5" />
              <circle cx="17.5" cy="18" r="1.5" />
            </>
          }
        >
          {messages.shippingText}
        </Benefit>
        {providers.length > 0 && (
          <Benefit
            title={messages.paymentsTitle}
            icon={
              <>
                <rect x="2.5" y="5" width="19" height="14" rx="2" />
                <path d="M2.5 9.5h19M6 15h4" />
              </>
            }
          >
            {es.faq.summary.payWith(paymentMethodNames(providers))}
          </Benefit>
        )}
        <Benefit
          title={messages.securityTitle}
          icon={
            <>
              <rect x="5" y="10.5" width="14" height="10" rx="2" />
              <path d="M8 10.5V7.5a4 4 0 0 1 8 0v3M12 14.5v2" />
            </>
          }
        >
          {messages.securityText}
        </Benefit>
      </ul>
    </section>
  );
}
