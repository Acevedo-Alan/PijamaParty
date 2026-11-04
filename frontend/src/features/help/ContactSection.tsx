import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { hasContactSection, whatsAppUrl } from './storeContact.ts';
import { useStoreContact } from './useStoreContact.ts';
import './help.css';

const messages = es.contact;

const CONTACT_ID = 'contactanos';

/**
 * "Contactanos" at the end of the home page (UC-30, D-09): the email and WhatsApp as links, no form. What the store
 * has not set in "Datos de la tienda" (UC-36) is left out, and without both there is no section. Opened at
 * `/#contactanos`, the page scrolls to it.
 */
export function ContactSection() {
  const { hash, key } = useLocation();
  const section = useRef<HTMLElement>(null);
  const contact = useStoreContact();

  // The router does not scroll to a hash: each link to it, even from the home page itself, comes here again.
  useEffect(() => {
    if (hash === `#${CONTACT_ID}`) {
      section.current?.scrollIntoView({ block: 'start' });
    }
  }, [hash, key]);

  if (!hasContactSection(contact)) {
    return null;
  }
  const { email, whatsAppNumber } = contact;
  return (
    <section ref={section} className="wrap contact" aria-labelledby={CONTACT_ID}>
      <h2 className="section-title" id={CONTACT_ID}>
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
