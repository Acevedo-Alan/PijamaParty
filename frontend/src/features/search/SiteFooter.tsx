import type { ReactNode } from 'react';
import { es } from '../../i18n/es.ts';
import { ConsentFooterLink } from '../analytics/ConsentBanner.tsx';
import type { PaymentProvider } from '../checkout/checkoutForm.ts';
import { usePaymentProviders } from '../checkout/useCheckout.ts';
import { FaqLink } from '../help/FaqLink.tsx';
import { STORE_CONTACT, whatsAppUrl } from '../help/storeContact.ts';
import { WithdrawalLink } from '../withdrawal/WithdrawalLink.tsx';
import './footer.css';

const messages = es.footer;

/** Outline drawings of a phone and an envelope, before the number and the email (T-E7-24). */
const phoneIcon = (
  <svg className="footer-icon" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M5 4.5h3.2l1.6 4-2 1.3a10.5 10.5 0 0 0 6.4 6.4l1.3-2 4 1.6V19a1.5 1.5 0 0 1-1.6 1.5A15.5 15.5 0 0 1 3.5 6.1 1.5 1.5 0 0 1 5 4.5Z" />
  </svg>
);

const emailIcon = (
  <svg className="footer-icon" viewBox="0 0 24 24" aria-hidden="true">
    <rect x="3" y="5.5" width="18" height="13" rx="2" />
    <path d="m3.5 7 8.5 6 8.5-6" />
  </svg>
);

/** Outline drawings of the networks, not their logos; the link's name is the network's. */
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

/** "5493424000000" as "+54 9 3424000000": the country code and the mobile 9 apart, the rest as the business sent it. */
function displayWhatsApp(number: string): string {
  return `+${number.slice(0, 2)} ${number.slice(2, 3)} ${number.slice(3)}`;
}

/** The WhatsApp number and the email, as the business sends them (D-15, T-E7-24); without both, no column. */
function ContactColumn() {
  const { email, whatsAppNumber } = STORE_CONTACT;
  if (email === undefined && whatsAppNumber === undefined) {
    return null;
  }
  return (
    <section className="footer-column" aria-labelledby="footer-contact">
      <h2 id="footer-contact">{messages.contact}</h2>
      <ul className="footer-links">
        {whatsAppNumber !== undefined && (
          <li>
            <a href={whatsAppUrl(whatsAppNumber)} target="_blank" rel="noopener noreferrer">
              {phoneIcon}
              {displayWhatsApp(whatsAppNumber)}
            </a>
          </li>
        )}
        {email !== undefined && (
          <li>
            <a href={`mailto:${email}`}>
              {emailIcon}
              {email}
            </a>
          </li>
        )}
      </ul>
    </section>
  );
}

/**
 * Instagram and TikTok as round icon buttons (T-E7-24), as the business sends them (D-15); the network's name is the
 * link's name. Without both, no column.
 */
function SocialColumn() {
  const links = [
    { name: messages.instagram as string, url: STORE_CONTACT.instagramUrl, icon: instagramIcon },
    { name: messages.tiktok as string, url: STORE_CONTACT.tiktokUrl, icon: tiktokIcon },
  ].filter((link): link is { name: string; url: string; icon: React.JSX.Element } => link.url !== undefined);
  if (links.length === 0) {
    return null;
  }
  return (
    <section className="footer-column" aria-labelledby="footer-social">
      <h2 id="footer-social">{messages.social}</h2>
      <ul className="footer-social">
        {links.map((link) => (
          <li key={link.name}>
            <a href={link.url} target="_blank" rel="noopener noreferrer" aria-label={link.name} title={link.name}>
              {link.icon}
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
 * The footer of every public page (UC-31, T-E7-20, T-E7-24): Ayuda y legales, Contacto, Redes sociales and Medios de
 * pago; below them, after a line, the withdrawal button (UC-29, RNF-30.1), the link to change the analytics choice
 * (RNF-25) and the copyright. The exchanges and terms pages of T-E6-06 join Ayuda y legales once they exist.
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
        <SocialColumn />
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
