import { es } from '../../i18n/es.ts';
import { ConsentFooterLink } from '../analytics/ConsentBanner.tsx';
import { usePaymentProviders } from '../checkout/useCheckout.ts';
import { FaqLink } from '../help/FaqLink.tsx';
import { STORE_CONTACT } from '../help/storeContact.ts';
import { WithdrawalLink } from '../withdrawal/WithdrawalLink.tsx';
import './footer.css';

const messages = es.footer;

/** Instagram and TikTok, as the business sends them (D-15); without either, no column. */
function SocialColumn() {
  const links = [
    { name: messages.instagram as string, url: STORE_CONTACT.instagramUrl },
    { name: messages.tiktok as string, url: STORE_CONTACT.tiktokUrl },
  ].filter((link): link is { name: string; url: string } => link.url !== undefined);
  if (links.length === 0) {
    return null;
  }
  return (
    <section className="footer-column" aria-labelledby="footer-social">
      <h2 id="footer-social">{messages.social}</h2>
      <ul className="footer-links">
        {links.map((link) => (
          <li key={link.name}>
            <a href={link.url} target="_blank" rel="noopener noreferrer">
              {link.name}
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** The enabled providers (T-E3b-04); while unknown, or when they cannot be read, no column. */
function PaymentsColumn() {
  const providers = usePaymentProviders().data ?? [];
  if (providers.length === 0) {
    return null;
  }
  return (
    <section className="footer-column" aria-labelledby="footer-payments">
      <h2 id="footer-payments">{messages.payments}</h2>
      <ul className="footer-payments">
        {providers.map((provider) => (
          <li key={provider}>{es.checkout.providers[provider].name}</li>
        ))}
      </ul>
    </section>
  );
}

/**
 * The footer of every public page (UC-31): Ayuda, Redes and Medios de pago; below them, the withdrawal button (UC-29,
 * RNF-30.1), the link to change the analytics choice (RNF-25) and the copyright. The exchanges and terms pages of
 * T-E6-06 join Ayuda once they exist.
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
