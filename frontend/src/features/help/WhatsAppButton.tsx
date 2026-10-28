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
      {/* The store's own drawing in WhatsApp's style, not its logo: a round speech bubble with a filled phone. */}
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path className="whatsapp-bubble" d="M12 3.2a8.8 8.8 0 0 0-7.6 13.2L3.2 20.8l4.5-1.2A8.8 8.8 0 1 0 12 3.2Z" />
        <path
          className="whatsapp-phone"
          d="M9.3 7.6h-.8c-.3 0-.7.1-1 .5-.4.4-.9 1.1-.9 2.3 0 1.4 1 2.8 1.2 3 .2.2 2 3 4.9 4.2 2.4 1 2.9.8 3.5.7.5-.1 1.6-.7 1.9-1.4.2-.6.2-1.2.1-1.3l-.5-.4-1.8-.9c-.3-.1-.4-.1-.6.1l-.8 1c-.2.2-.3.2-.6.1a7 7 0 0 1-3.6-3.2c-.2-.3 0-.4.1-.6l.4-.5.3-.5v-.5l-.8-2c-.2-.5-.4-.5-.6-.5Z"
        />
      </svg>
    </a>
  );
}
