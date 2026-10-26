import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { ConsentFooterLink } from '../analytics/ConsentBanner.tsx';
import type { PaymentProvider } from '../checkout/checkoutForm.ts';
import { usePaymentProviders } from '../checkout/useCheckout.ts';
import { FaqLink } from '../help/FaqLink.tsx';
import { mapsUrl, STORE_CONTACT, whatsAppUrl } from '../help/storeContact.ts';
import { WithdrawalLink } from '../withdrawal/WithdrawalLink.tsx';
import { StoreLogo } from './StoreLogo.tsx';
import { StoreNavigation } from './StoreNavigation.tsx';
import './footer.css';

const messages = es.footer;

/** Outline drawings of a phone, an envelope, a clock and a pin, before each contact (T-E7-24, T-E7-26). */
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

const clockIcon = (
  <svg className="footer-icon" viewBox="0 0 24 24" aria-hidden="true">
    <circle cx="12" cy="12" r="8.5" />
    <path d="M12 7.5V12l3 2" />
  </svg>
);

const pinIcon = (
  <svg className="footer-icon" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z" />
    <circle cx="12" cy="10" r="2.3" />
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

/** The store's own drawing of a parcel for Correo Argentino, not its logo (T-E7-26). */
const parcelIcon = (
  <svg className="footer-payment-icon" viewBox="0 0 32 24" aria-hidden="true">
    <path d="M16 3 6 7.5v9L16 21l10-4.5v-9L16 3Z" />
    <path d="M6 7.5 16 12l10-4.5M16 12v9M11 5.3l10 4.5" />
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

/**
 * The WhatsApp number, the email, the opening hours and the address, as the business sends them (D-15, T-E7-24,
 * T-E7-26); without any of them, no column.
 */
function ContactColumn() {
  const { email, whatsAppNumber, openingHours, address } = STORE_CONTACT;
  if (email === undefined && whatsAppNumber === undefined && openingHours === undefined && address === undefined) {
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
        {openingHours !== undefined && (
          <li className="footer-text">
            {clockIcon}
            {openingHours}
          </li>
        )}
        {address !== undefined && (
          <li>
            <a href={mapsUrl(address)} target="_blank" rel="noopener noreferrer" title={messages.maps}>
              {pinIcon}
              {address}
            </a>
          </li>
        )}
      </ul>
    </section>
  );
}

/**
 * Instagram and TikTok as round icon buttons (T-E7-24), as the business sends them (D-15); the network's name is the
 * link's name. Without both, nothing.
 */
function SocialLinks() {
  const links = [
    { name: messages.instagram as string, url: STORE_CONTACT.instagramUrl, icon: instagramIcon },
    { name: messages.tiktok as string, url: STORE_CONTACT.tiktokUrl, icon: tiktokIcon },
  ].filter((link): link is { name: string; url: string; icon: React.JSX.Element } => link.url !== undefined);
  if (links.length === 0) {
    return null;
  }
  return (
    <section className="footer-social-block" aria-labelledby="footer-social">
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

/** The enabled providers (T-E3b-04) as icons in a row; while unknown, or when they cannot be read, nothing. */
function PaymentProviders() {
  const providers = usePaymentProviders().data ?? [];
  if (providers.length === 0) {
    return null;
  }
  return (
    <section aria-labelledby="footer-payments">
      <h2 id="footer-payments">{messages.payments}</h2>
      <ul className="footer-payments">
        {providers.map((provider) => {
          const name = es.checkout.providers[provider].name;
          return (
            <li key={provider} title={name} translate="no">
              {paymentIcons[provider]}
              <span className="visually-hidden">{name}</span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

/** The payment providers, then the carrier, which is always there (T-E7-26). */
function PaymentsColumn() {
  return (
    <div className="footer-column footer-payments-column">
      <PaymentProviders />
      <section aria-labelledby="footer-shipping">
        <h2 id="footer-shipping">{messages.shipping}</h2>
        <ul className="footer-payments">
          <li title={messages.carrier} translate="no">
            {parcelIcon}
            <span className="visually-hidden">{messages.carrier}</span>
          </li>
        </ul>
      </section>
    </div>
  );
}

/** The store's block that opens the footer (T-E7-25): the logo to the home page, the tagline and the networks. */
function StoreBlock() {
  return (
    <div className="footer-brand">
      <Link to="/" className="footer-logo">
        <StoreLogo />
      </Link>
      <p className="footer-tagline">{messages.tagline}</p>
      <SocialLinks />
    </div>
  );
}

/**
 * The footer of every public page (UC-31, T-E7-20, T-E7-24, T-E7-25): the store's block, then Tienda, Ayuda y
 * legales, Contacto and Medios de pago; below them, after a line, the withdrawal button (UC-29, RNF-30.1), the link to change the analytics choice
 * (RNF-25) and the copyright. The exchanges and terms pages of T-E6-06 join Ayuda y legales once they exist.
 */
export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="wrap site-footer-inner">
        <StoreBlock />
        <section className="footer-column" aria-labelledby="footer-shop">
          <h2 id="footer-shop">{messages.shop}</h2>
          <StoreNavigation className="footer-links" />
        </section>
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
