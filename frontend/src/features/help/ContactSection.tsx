import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { hasContactSection, whatsAppUrl } from './storeContact.ts';
import { useStoreContact } from './useStoreContact.ts';
import './help.css';

const messages = es.contact;

const CONTACT_ID = 'contactanos';

/** Set on `<html>` while the section is on screen; whatsAppButton.css hides the floating button then. */
const CONTACT_IN_VIEW = 'data-contact-in-view';

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

  const shown = hasContactSection(contact);

  // T-E7-42: while this section is on screen the floating WhatsApp button hides (its CSS reads this attribute).
  useEffect(() => {
    const element = section.current;
    if (!shown || element === null || typeof IntersectionObserver === 'undefined') {
      return;
    }
    const root = document.documentElement;
    const observer = new IntersectionObserver(([entry]) => {
      if (entry?.isIntersecting === true) {
        root.setAttribute(CONTACT_IN_VIEW, '');
      } else {
        root.removeAttribute(CONTACT_IN_VIEW);
      }
    });
    observer.observe(element);
    return () => {
      observer.disconnect();
      root.removeAttribute(CONTACT_IN_VIEW);
    };
  }, [shown]);

  if (!shown) {
    return null;
  }
  const { email, whatsAppNumber } = contact;
  return (
    <section ref={section} className="wrap contact" aria-labelledby={CONTACT_ID}>
      <div className="contact-text">
        <h2 className="section-title" id={CONTACT_ID}>
          {messages.title}
        </h2>
        <p>{messages.text}</p>
        {email !== undefined && (
          <p className="contact-email">
            <a href={`mailto:${email}`}>{email}</a>
          </p>
        )}
      </div>
      {whatsAppNumber !== undefined && (
        <a className="btn contact-whatsapp" href={whatsAppUrl(whatsAppNumber)} target="_blank" rel="noopener noreferrer">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path className="whatsapp-bubble" d="M12 3.2a8.8 8.8 0 0 0-7.6 13.2L3.2 20.8l4.5-1.2A8.8 8.8 0 1 0 12 3.2Z" />
            <path
              className="whatsapp-phone"
              d="M9.3 7.6h-.8c-.3 0-.7.1-1 .5-.4.4-.9 1.1-.9 2.3 0 1.4 1 2.8 1.2 3 .2.2 2 3 4.9 4.2 2.4 1 2.9.8 3.5.7.5-.1 1.6-.7 1.9-1.4.2-.6.2-1.2.1-1.3l-.5-.4-1.8-.9c-.3-.1-.4-.1-.6.1l-.8 1c-.2.2-.3.2-.6.1a7 7 0 0 1-3.6-3.2c-.2-.3 0-.4.1-.6l.4-.5.3-.5v-.5l-.8-2c-.2-.5-.4-.5-.6-.5Z"
            />
          </svg>
          {messages.whatsApp}
        </a>
      )}
    </section>
  );
}
