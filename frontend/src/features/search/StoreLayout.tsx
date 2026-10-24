import { Outlet, useLocation } from 'react-router';
import { Analytics } from '../analytics/ConsentBanner.tsx';
import { AccountDeletedNotice } from '../account/AccountDataSection.tsx';
import { CartPanel } from '../cart/CartPanel.tsx';
import { CartPanelProvider } from '../cart/CartPanelContext.tsx';
import { NoIndex } from '../seo/NoIndex.tsx';
import { isPrivatePath } from '../seo/privatePaths.ts';
import { SiteFooter } from './SiteFooter.tsx';
import { SiteHeader } from './SiteHeader.tsx';
import './search.css';

/**
 * The frame of every public page: the header and the footer (UC-31), the cart side panel (UC-08), the analytics
 * consent (RNF-25), which the back office never shows, and the message after the customer deleted its account
 * (UC-28). The account, checkout, tracking and search pages are kept out of search engines (T-E6-23).
 */
export function StoreLayout() {
  const { pathname } = useLocation();
  return (
    <CartPanelProvider>
      {isPrivatePath(pathname) && <NoIndex />}
      <SiteHeader />
      {/* Before the page, so a returning visitor's tools are loaded when the page sends its first events. */}
      <Analytics />
      <AccountDeletedNotice />
      <Outlet />
      <SiteFooter />
      <CartPanel />
    </CartPanelProvider>
  );
}
