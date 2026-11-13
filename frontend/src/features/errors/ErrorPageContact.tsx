import { es } from '../../i18n/es.ts';
import { whatsAppUrl } from '../help/storeContact.ts';
import { useStoreContact } from '../help/useStoreContact.ts';

const messages = es.errorPages;

/**
 * The way to the contact of the 404 and the error page (RNF-31.7, T-E6-25): the store's WhatsApp and email from
 * "Datos de la tienda" (UC-36). What the store has not set is left out, and with neither there is no line.
 */
export function ErrorPageContact() {
  const { email, whatsAppNumber } = useStoreContact();
  if (email === undefined && whatsAppNumber === undefined) {
    return null;
  }
  return (
    <p className="error-page-contact">
      {messages.needHelp}{' '}
      {whatsAppNumber !== undefined && (
        <a
          href={whatsAppUrl(whatsAppNumber)}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={es.contact.whatsAppButtonLabel}
        >
          {es.contact.whatsAppButton}
        </a>
      )}
      {email !== undefined && (
        <>
          {whatsAppNumber === undefined ? messages.writeTo : ` ${messages.orTo}`}{' '}
          <a href={`mailto:${email}`}>{email}</a>
        </>
      )}
    </p>
  );
}
