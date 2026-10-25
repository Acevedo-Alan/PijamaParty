import { m } from 'motion/react';
import type { ReactNode } from 'react';
import { Outlet, useLocation } from 'react-router';
import { useEntranceMotion } from '../../components/motion/useActionMotion.ts';
import { Analytics } from '../analytics/ConsentBanner.tsx';
import { AccountDeletedNotice } from '../account/AccountDataSection.tsx';
import { CartPanel } from '../cart/CartPanel.tsx';
import { CartPanelProvider } from '../cart/CartPanelContext.tsx';
import { NoIndex } from '../seo/NoIndex.tsx';
import { isPrivatePath } from '../seo/privatePaths.ts';
import { BenefitsBar } from './BenefitsBar.tsx';
import { SiteFooter } from './SiteFooter.tsx';
import { SiteHeader } from './SiteHeader.tsx';
import './search.css';

/**
 * A page of the store, which rises and fades in when the visitor moves to it (T-E7-17). The store's first page shows
 * in place: Motion's features load after the first paint, so its largest paint is never delayed (RNF-11).
 */
function PageEntrance({ children }: { children: ReactNode }) {
  return <m.div {...useEntranceMotion()}>{children}</m.div>;
}

/**
 * The frame of every public page: the header, the benefits bar and the footer (UC-31), the cart side panel (UC-08), the analytics
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
      {/* A new path is a new page, which enters; a new query on the same page (filters, sort) is not. */}
      <PageEntrance key={pathname}>
        <Outlet />
      </PageEntrance>
      <BenefitsBar />
      <SiteFooter />
      <CartPanel />
    </CartPanelProvider>
  );
}
