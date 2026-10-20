import { es } from '../../i18n/es.ts';
import { STORE_CONTACT, whatsAppUrl } from './storeContact.ts';
import './help.css';

const messages = es.contact;

/**
 * "Contactanos" at the end of the home page (UC-30, D-09): the email and WhatsApp as links, no form. What the
 * business has not sent yet is left out (D-15), and without both there is no section.
 */
export function ContactSection() {
  const { email, whatsAppNumber } = STORE_CONTACT;
  if (email === undefined && whatsAppNumber === undefined) {
    return null;
  }
  return (
    <section className="wrap contact" aria-labelledby="contactanos">
      <h2 className="section-title" id="contactanos">
        {messages.title}
      </h2>
      <p>{messages.text}</p>
      {email !== undefined && (
        <p className="contact-email">
          <a href={`mailto:${email}`}>{email}</a>
        </p>
      )}
      {whatsAppNumber !== undefined && (
        <a className="btn btn-secondary contact-whatsapp" href={whatsAppUrl(whatsAppNumber)} target="_blank" rel="noopener noreferrer">
          {messages.whatsApp}
        </a>
      )}
    </section>
  );
}
