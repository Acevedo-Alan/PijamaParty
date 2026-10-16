import { Link, Outlet } from 'react-router';
import { es } from '../../i18n/es.ts';
import { Analytics, StoreFooter } from '../analytics/ConsentBanner.tsx';
import { AccountLink } from '../account/AccountLink.tsx';
import { CartButton } from '../cart/CartButton.tsx';
import { CartPanel } from '../cart/CartPanel.tsx';
import { CartPanelProvider } from '../cart/CartPanelContext.tsx';
import { HeaderSearch } from './HeaderSearch.tsx';
import './search.css';

/**
 * The frame of every public page: the header with the store name, the search (UC-26), the account entry (UC-19)
 * and the cart button, the cart side panel (UC-08), the footer, and the analytics consent (RNF-25), which the back
 * office never shows.
 */
export function StoreLayout() {
  return (
    <CartPanelProvider>
      <header className="site">
        <div className="wrap header-row">
          <Link to="/" className="logo">
            {es.store.name}
          </Link>
          <div className="header-actions">
            <HeaderSearch />
            <AccountLink />
            <CartButton />
          </div>
        </div>
      </header>
      {/* Before the page, so a returning visitor's tools are loaded when the page sends its first events. */}
      <Analytics />
      <Outlet />
      <StoreFooter />
      <CartPanel />
    </CartPanelProvider>
  );
}
