import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { hasContactSection, STORE_CONTACT, whatsAppUrl } from './storeContact.ts';
import './help.css';

const messages = es.contact;

const CONTACT_ID = 'contactanos';

/** Where "Contacto" of the footer goes (UC-31, T-E7-20): this section of the home page. */
export const CONTACT_PATH = `/#${CONTACT_ID}`;

/**
 * "Contactanos" at the end of the home page (UC-30, D-09): the email and WhatsApp as links, no form. What the
 * business has not sent yet is left out (D-15), and without both there is no section. Opened from "Contacto" of the
 * footer, the page scrolls to it.
 */
export function ContactSection() {
  const { hash, key } = useLocation();
  const section = useRef<HTMLElement>(null);

  // The router does not scroll to a hash: each "Contacto", even from the home page itself, comes here again.
  useEffect(() => {
    if (hash === `#${CONTACT_ID}`) {
      section.current?.scrollIntoView({ block: 'start' });
    }
  }, [hash, key]);

  if (!hasContactSection()) {
    return null;
  }
  const { email, whatsAppNumber } = STORE_CONTACT;
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
