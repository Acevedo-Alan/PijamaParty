import { Outlet } from 'react-router';
import { Analytics, StoreFooter } from '../analytics/ConsentBanner.tsx';
import { AccountDeletedNotice } from '../account/AccountDataSection.tsx';
import { CartPanel } from '../cart/CartPanel.tsx';
import { CartPanelProvider } from '../cart/CartPanelContext.tsx';
import { SiteHeader } from './SiteHeader.tsx';
import './search.css';

/**
 * The frame of every public page: the header (UC-31), the cart side panel (UC-08), the footer, the analytics
 * consent (RNF-25), which the back office never shows, and the message after the customer deleted its account
 * (UC-28).
 */
export function StoreLayout() {
  return (
    <CartPanelProvider>
      <SiteHeader />
      {/* Before the page, so a returning visitor's tools are loaded when the page sends its first events. */}
      <Analytics />
      <AccountDeletedNotice />
      <Outlet />
      <StoreFooter />
      <CartPanel />
    </CartPanelProvider>
  );
}
