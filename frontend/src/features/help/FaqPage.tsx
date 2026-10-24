import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { usePaymentProviders } from '../checkout/useCheckout.ts';
import { PageMeta } from '../seo/PageMeta.tsx';
import { WITHDRAWAL_PATH } from '../withdrawal/WithdrawalLink.tsx';
import { RETURNS_POLICY_PATH } from './FaqLink.tsx';
import { useShippingRates } from './useShippingRates.ts';
import './help.css';

const messages = es.faq;

/** What failed to load, with a retry; the fixed answers stay on the page. */
function LoadError({ text, onRetry }: { text: string; onRetry: () => void }) {
  return (
    <div role="alert" className="faq-error">
      <p>{text}</p>
      <button type="button" className="btn btn-secondary" onClick={onRetry}>
        {messages.retry}
      </button>
    </div>
  );
}

/** The costs of UC-12: only the provinces with shipping, and the free-shipping threshold when there is one. */
function ShippingAnswer() {
  const rates = useShippingRates();
  if (rates.isPending) {
    return <p role="status">{messages.shipping.loading}</p>;
  }
  if (rates.isError) {
    return <LoadError text={messages.shipping.loadError} onRetry={() => void rates.refetch()} />;
  }
  const { zones, freeShippingThresholdCents } = rates.data;
  if (zones.length === 0) {
    return <p>{messages.shipping.none}</p>;
  }
  return (
    <>
      <p>{messages.shipping.intro}</p>
      <div className="faq-table" role="region" aria-label={messages.shipping.table} tabIndex={0}>
        <table>
          <thead>
            <tr>
              <th scope="col">{messages.shipping.province}</th>
              <th scope="col">{messages.shipping.cost}</th>
            </tr>
          </thead>
          <tbody>
            {zones.map(({ province, costCents }) => (
              <tr key={province}>
                <th scope="row">{es.provinces[province]}</th>
                <td>{costCents === 0 ? messages.shipping.free : formatMoney(costCents)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p>{messages.shipping.notListed}</p>
      {freeShippingThresholdCents !== null && (
        <p className="faq-highlight">{messages.shipping.freeFrom(formatMoney(freeShippingThresholdCents))}</p>
      )}
    </>
  );
}

/** The methods the checkout offers (T-E3b-04), with the hints of its step 2. */
function PaymentAnswer() {
  const providers = usePaymentProviders();
  if (providers.isPending) {
    return <p role="status">{messages.payment.loading}</p>;
  }
  if (providers.isError) {
    return <LoadError text={messages.payment.loadError} onRetry={() => void providers.refetch()} />;
  }
  return (
    <>
      <p>{messages.payment.intro}</p>
      <ul className="faq-list">
        {providers.data.map((provider) => (
          <li key={provider}>
            <strong>{es.checkout.providers[provider].name}</strong>: {es.checkout.providers[provider].hint}
          </li>
        ))}
      </ul>
    </>
  );
}

/**
 * RNF-31.6 (T-E5-12): shipping costs by province, delivery times, payment methods, and exchanges and returns. The
 * texts are fixed; the costs and the payment methods are the server's, so they match what the checkout charges.
 */
export function FaqPage() {
  return (
    <main className="wrap faq">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <PageMeta description={es.seo.faqDescription} />
      <h1>{messages.title}</h1>
      <details open>
        <summary>{messages.shipping.question}</summary>
        <div className="faq-answer">
          <ShippingAnswer />
        </div>
      </details>
      <details>
        <summary>{messages.delivery.question}</summary>
        <div className="faq-answer">
          <p>{messages.delivery.answer}</p>
          <p>{messages.delivery.tracking}</p>
        </div>
      </details>
      <details>
        <summary>{messages.payment.question}</summary>
        <div className="faq-answer">
          <PaymentAnswer />
        </div>
      </details>
      <details>
        <summary>{messages.returns.question}</summary>
        <div className="faq-answer">
          <p>
            {messages.returns.withdrawal} <Link to={WITHDRAWAL_PATH}>{es.withdrawal.link}</Link>.
          </p>
          <p>
            {messages.returns.policy} <Link to={RETURNS_POLICY_PATH}>{messages.returns.policyLink}</Link>.
          </p>
        </div>
      </details>
    </main>
  );
}
