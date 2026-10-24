import { Link } from 'react-router';
import logo144 from '../../assets/brand/logo-144.webp';
import logo48 from '../../assets/brand/logo-48.webp';
import logo96 from '../../assets/brand/logo-96.webp';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { HeaderAccount } from '../account/AccountLink.tsx';
import { CartButton } from '../cart/CartButton.tsx';
import { useShippingRates } from '../help/useShippingRates.ts';
import { HeaderSearch } from './HeaderSearch.tsx';
import { MobileMenu } from './MobileMenu.tsx';
import { StoreNavigation } from './StoreNavigation.tsx';

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
 * in the phone menu), the search (UC-26), the account entry (UC-19) and the cart button (UC-08).
 */
export function SiteHeader() {
  return (
    <>
      <FreeShippingStrip />
      <header className="site">
        <div className="wrap header-row">
          <MobileMenu />
          <Link to="/" className="logo">
            {/* The business's logo (D-15), 48 px tall as in the prototype, fixed in the build (D-03). */}
            <img
              src={logo48}
              srcSet={`${logo48} 1x, ${logo96} 2x, ${logo144} 3x`}
              width={58}
              height={48}
              alt={es.store.name}
            />
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
