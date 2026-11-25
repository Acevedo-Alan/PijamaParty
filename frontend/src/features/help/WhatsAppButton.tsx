import { useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { whatsAppUrl } from './storeContact.ts';
import { WhatsAppGlyph } from './WhatsAppGlyph.tsx';
import { useStoreContact } from './useStoreContact.ts';
import './whatsAppButton.css';

/** Where the button would get in the way of paying or of the customer's own data (T-E7-27). */
const HIDDEN_PREFIXES = ['/checkout', '/cuenta'];

function isHidden(pathname: string): boolean {
  return HIDDEN_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

/**
 * The round WhatsApp button fixed at the bottom right of the store (UC-31, T-E7-27): the chat with the business, in a
 * new tab. Not in the checkout or the account, and nowhere without the store's number (UC-36). The CSS lifts
 * it above the product page's add-to-cart bar while that bar shows.
 */
export function WhatsAppButton() {
  const { pathname } = useLocation();
  const number = useStoreContact().whatsAppNumber;
  if (number === undefined || isHidden(pathname)) {
    return null;
  }
  return (
    <a
      className="whatsapp-button"
      href={whatsAppUrl(number)}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={es.contact.whatsAppButtonLabel}
      title={es.contact.whatsAppButtonLabel}
    >
      <WhatsAppGlyph />
    </a>
  );
}
