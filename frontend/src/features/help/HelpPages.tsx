import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { PageMeta } from '../seo/PageMeta.tsx';
import { WITHDRAWAL_PATH } from '../withdrawal/WithdrawalLink.tsx';
import { FAQ_PATH, RETURNS_POLICY_PATH } from './FaqLink.tsx';
import './help.css';

/** One help page (T-E7-28): its title in the tab, its description and canonical link (T-E6-23), and its heading. */
function HelpPage({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <main className="wrap help-page">
      <title>{`${title} | ${es.store.name}`}</title>
      <PageMeta description={description} />
      <h1>{title}</h1>
      {children}
    </main>
  );
}

/** UC-31 (T-E7-28): the five steps of a purchase, as the store works, and the FAQ for the rest. */
export function HowToBuyPage() {
  const messages = es.howToBuy;
  return (
    <HelpPage title={messages.title} description={es.seo.howToBuyDescription}>
      <p className="help-intro">{messages.intro}</p>
      <ol className="help-steps">
        {messages.steps.map((step) => (
          <li key={step.title}>
            <h2>{step.title}</h2>
            <p>{step.text}</p>
          </li>
        ))}
      </ol>
      <p>
        {messages.more} <Link to={FAQ_PATH}>{messages.moreLink}</Link>.
      </p>
    </HelpPage>
  );
}

/** UC-31 (T-E7-28): the exchanges policy (D-18) and, apart, the withdrawal right (UC-29). */
export function ReturnsPolicyPage() {
  const messages = es.returnsPolicy;
  return (
    <HelpPage title={messages.title} description={es.seo.returnsDescription}>
      <section aria-labelledby="returns-exchanges">
        <h2 id="returns-exchanges">{messages.exchangesTitle}</h2>
        <ul className="help-list">
          {messages.exchanges.map((rule) => (
            <li key={rule}>{rule}</li>
          ))}
        </ul>
        <p>{messages.howTo}</p>
      </section>
      <section aria-labelledby="returns-withdrawal">
        <h2 id="returns-withdrawal">{messages.withdrawalTitle}</h2>
        <p>{messages.withdrawal}</p>
        <p>
          {messages.withdrawalLink} <Link to={WITHDRAWAL_PATH}>{es.withdrawal.link}</Link>.
        </p>
      </section>
    </HelpPage>
  );
}

/** UC-31 (T-E7-28, RNF-30.6): the terms of purchase, a draft until T-E6-21. */
export function TermsPage() {
  const messages = es.terms;
  const { acceptance, products, purchase, shipping, data, law, contact } = messages.sections;
  const section = ({ title, text }: { title: string; text: string }) => (
    <section key={title}>
      <h2>{title}</h2>
      <p>{text}</p>
    </section>
  );
  return (
    <HelpPage title={messages.title} description={es.seo.termsDescription}>
      {[acceptance, products, purchase, shipping].map(section)}
      <section>
        <h2>{messages.exchangesTitle}</h2>
        <p>
          {messages.exchangesBefore} <Link to={RETURNS_POLICY_PATH}>{messages.policyLink}</Link>{' '}
          {messages.exchangesBetween} <Link to={WITHDRAWAL_PATH}>{es.withdrawal.link}</Link>.
        </p>
      </section>
      {[data, law, contact].map(section)}
    </HelpPage>
  );
}
