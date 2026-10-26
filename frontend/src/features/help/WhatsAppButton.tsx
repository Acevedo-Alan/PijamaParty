import { useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { STORE_CONTACT, whatsAppUrl } from './storeContact.ts';
import './whatsAppButton.css';

/** Where the button would get in the way of paying or of the customer's own data (T-E7-27). */
const HIDDEN_PREFIXES = ['/checkout', '/cuenta'];

function isHidden(pathname: string): boolean {
  return HIDDEN_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

/**
 * The round WhatsApp button fixed at the bottom right of the store (UC-31, T-E7-27): the chat with the business, in a
 * new tab. Not in the checkout or the account, and nowhere until the business sends its number (D-15). The CSS lifts
 * it above the product page's add-to-cart bar while that bar shows.
 */
export function WhatsAppButton() {
  const { pathname } = useLocation();
  const number = STORE_CONTACT.whatsAppNumber;
  if (number === undefined || isHidden(pathname)) {
    return null;
  }
  return (
    <a
      className="whatsapp-button"
      href={whatsAppUrl(number)}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={es.contact.whatsAppButton}
      title={es.contact.whatsAppButton}
    >
      {/* The store's own drawing of a speech bubble with a phone, not WhatsApp's logo. */}
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3.5a8.5 8.5 0 0 0-7.4 12.7L3.5 20.5l4.4-1.1A8.5 8.5 0 1 0 12 3.5Z" />
        <path d="M9.2 8.2h1.4l.7 1.8-.9.7a5 5 0 0 0 2.9 2.9l.7-.9 1.8.7v1.4a1 1 0 0 1-1 1A6.6 6.6 0 0 1 8.2 9.2a1 1 0 0 1 1-1Z" />
      </svg>
    </a>
  );
}
