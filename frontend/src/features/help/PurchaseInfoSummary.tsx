import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { usePaymentProviders } from '../checkout/useCheckout.ts';
import { FAQ_PATH } from './FaqLink.tsx';
import { paymentMethodNames } from './paymentMethods.ts';
import { useShippingRates } from './useShippingRates.ts';
import './help.css';

const messages = es.faq.summary;

/**
 * RNF-31.6 (T-E5-12): below the add to cart, the free-shipping threshold (when there is one), the delivery times and
 * the payment methods, with a link to the FAQ. What could not be read is left out.
 */
export function PurchaseInfoSummary() {
  const threshold = useShippingRates().data?.freeShippingThresholdCents ?? null;
  const providers = usePaymentProviders().data ?? [];
  return (
    <section className="purchase-info" aria-label={messages.label}>
      <ul>
        {threshold !== null && <li>{messages.freeFrom(formatMoney(threshold))}</li>}
        <li>{messages.delivery}</li>
        {providers.length > 0 && <li>{messages.payWith(paymentMethodNames(providers))}</li>}
      </ul>
      <Link to={FAQ_PATH}>{messages.link}</Link>
    </section>
  );
}
