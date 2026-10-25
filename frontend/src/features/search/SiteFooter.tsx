import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { ConsentFooterLink } from '../analytics/ConsentBanner.tsx';
import type { PaymentProvider } from '../checkout/checkoutForm.ts';
import { usePaymentProviders } from '../checkout/useCheckout.ts';
import { CONTACT_PATH } from '../help/ContactSection.tsx';
import { FaqLink } from '../help/FaqLink.tsx';
import { hasContactSection, STORE_CONTACT } from '../help/storeContact.ts';
import { WithdrawalLink } from '../withdrawal/WithdrawalLink.tsx';
import './footer.css';

const messages = es.footer;

/** Outline drawings of the networks, not their logos; the link's text names the network. */
const instagramIcon = (
  <svg className="footer-icon" viewBox="0 0 24 24" aria-hidden="true">
    <rect x="3.5" y="3.5" width="17" height="17" rx="5" />
    <circle cx="12" cy="12" r="4" />
    <circle cx="17.2" cy="6.8" r="0.6" />
  </svg>
);

const tiktokIcon = (
  <svg className="footer-icon" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M13 3.5v11.5a3.5 3.5 0 1 1-3.5-3.5" />
    <path d="M13 3.5c.4 2.6 2.3 4.4 5 4.6" />
  </svg>
);

/** The store's own drawings of the providers, not their logos (T-E7-20): a card and a phone. */
const paymentIcons: Record<PaymentProvider, ReactNode> = {
  MERCADOPAGO: (
    <svg className="footer-payment-icon" viewBox="0 0 32 24" aria-hidden="true">
      <rect x="2" y="4" width="28" height="16" rx="3" />
      <path d="M2 9h28M6 15h6" />
    </svg>
  ),
  MODO: (
    <svg className="footer-payment-icon" viewBox="0 0 32 24" aria-hidden="true">
      <rect x="10" y="2" width="12" height="20" rx="2.5" />
      <path d="M14.5 18.5h3" />
    </svg>
  ),
};

/**
 * "Contacto", to the home page's "Contactanos", and Instagram and TikTok, as the business sends them (D-15); without
 * any of them, no column.
 */
function ContactColumn() {
  const links = [
    { name: messages.instagram as string, url: STORE_CONTACT.instagramUrl, icon: instagramIcon },
    { name: messages.tiktok as string, url: STORE_CONTACT.tiktokUrl, icon: tiktokIcon },
  ].filter((link): link is { name: string; url: string; icon: React.JSX.Element } => link.url !== undefined);
  const showContact = hasContactSection();
  if (links.length === 0 && !showContact) {
    return null;
  }
  return (
    <section className="footer-column" aria-labelledby="footer-social">
      <h2 id="footer-social">{messages.social}</h2>
      <ul className="footer-links">
        {showContact && (
          <li>
            <Link to={CONTACT_PATH}>{messages.contact}</Link>
          </li>
        )}
        {links.map((link) => (
          <li key={link.name}>
            <a href={link.url} target="_blank" rel="noopener noreferrer">
              {link.icon}
              {link.name}
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** The enabled providers (T-E3b-04) as icons in a row; while unknown, or when they cannot be read, no column. */
function PaymentsColumn() {
  const providers = usePaymentProviders().data ?? [];
  if (providers.length === 0) {
    return null;
  }
  return (
    <section className="footer-column" aria-labelledby="footer-payments">
      <h2 id="footer-payments">{messages.payments}</h2>
      <ul className="footer-payments">
        {providers.map((provider) => {
          const name = es.checkout.providers[provider].name;
          return (
            <li key={provider} title={name}>
              {paymentIcons[provider]}
              <span className="visually-hidden">{name}</span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

/**
 * The footer of every public page (UC-31, T-E7-20): Ayuda y legales, Contacto y redes and Medios de pago; below them,
 * after a line, the withdrawal button (UC-29, RNF-30.1), the link to change the analytics choice (RNF-25) and the
 * copyright. The exchanges and terms pages of T-E6-06 join Ayuda y legales once they exist.
 */
export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="wrap site-footer-inner">
        <section className="footer-column" aria-labelledby="footer-help">
          <h2 id="footer-help">{messages.help}</h2>
          <ul className="footer-links">
            <li>
              <FaqLink />
            </li>
          </ul>
        </section>
        <ContactColumn />
        <PaymentsColumn />
        <div className="footer-bottom">
          <WithdrawalLink />
          <ConsentFooterLink />
          <p>{messages.copyright(new Date().getFullYear(), es.store.name)}</p>
        </div>
      </div>
    </footer>
  );
}
