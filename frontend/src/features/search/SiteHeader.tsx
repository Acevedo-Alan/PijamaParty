import { useRef } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { HeaderAccount } from '../account/AccountLink.tsx';
import { CartButton } from '../cart/CartButton.tsx';
import { useShippingRates } from '../help/useShippingRates.ts';
import { HeaderSearch } from './HeaderSearch.tsx';
import { MobileMenu } from './MobileMenu.tsx';
import { StoreLogo } from './StoreLogo.tsx';
import { StoreNavigation } from './StoreNavigation.tsx';
import { useHeaderScroll } from './useHeaderScroll.ts';

/** "Envío gratis desde $X" with a free-shipping threshold (UC-31); without one, or while it is unknown, nothing. */
function FreeShippingStrip() {
  const threshold = useShippingRates().data?.freeShippingThresholdCents ?? null;
  if (threshold === null) {
    return null;
  }
  return <p className="announce">{es.header.freeShippingFrom(formatMoney(threshold))}</p>;
}

/**
 * The header of every public page (UC-31): the free-shipping strip, the logo, the navigation (from 860 px; below,
 * in the phone menu), the search (UC-26), the account entry (UC-19) and the cart button (UC-08). On a phone it hides
 * while the visitor scrolls down and comes back when they scroll up (T-E7-15); once the page has scrolled, its
 * background turns translucent (T-E7-16), and from 860 px it becomes a floating pill (T-E7-30).
 */
export function SiteHeader() {
  const header = useRef<HTMLElement>(null);
  const { scrolled, hidden } = useHeaderScroll(header);

  return (
    <>
      <FreeShippingStrip />
      <header
        className="site"
        ref={header}
        data-scrolled={scrolled || undefined}
        data-hidden={hidden || undefined}
      >
        <div className="wrap header-row">
          <MobileMenu />
          <Link to="/" className="logo">
            <StoreLogo />
          </Link>
          <nav className="header-nav" aria-label={es.header.navigation}>
            <StoreNavigation className="nav-links" />
          </nav>
          <div className="header-tools">
            <HeaderSearch />
            <HeaderAccount />
            <CartButton />
          </div>
        </div>
      </header>
    </>
  );
}
