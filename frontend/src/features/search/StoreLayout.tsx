import { m } from 'motion/react';
import { useEffect, useRef, type ReactNode } from 'react';
import { Outlet, useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { useEntranceMotion } from '../../components/motion/useActionMotion.ts';
import { Analytics } from '../analytics/ConsentBanner.tsx';
import { AccountDeletedNotice } from '../account/AccountDeletedNotice.tsx';
import { CartPanelOnDemand } from '../cart/CartPanelOnDemand.tsx';
import { CartPanelProvider } from '../cart/CartPanelContext.tsx';
import { WhatsAppButton } from '../help/WhatsAppButton.tsx';
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

/** The page's heading, or its main region while the heading is not there yet. */
function pageTarget(): HTMLElement | null {
  return document.querySelector<HTMLElement>('main h1') ?? document.querySelector<HTMLElement>('main');
}

function focusPage() {
  const target = pageTarget();
  if (target !== null) {
    target.tabIndex = -1;
    target.focus({ preventScroll: true });
  }
}

/** WCAG 2.4.3: after moving to another page (not on the first one), focus starts at the page, not at <body>. */
function useFocusPageOnNavigation(pathname: string) {
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    focusPage();
  }, [pathname]);
}

/** WCAG 2.4.1: the first stop of the keyboard, which skips the header and goes to the page. */
function SkipLink() {
  return (
    <a
      className="skip-link"
      href="#main"
      onClick={(event) => {
        event.preventDefault();
        focusPage();
      }}
    >
      {es.skipToContent}
    </a>
  );
}

/**
 * The frame of every public page: the header, the footer, the benefits bar above it on the home page only (UC-31,
 * T-E7-23), the cart side panel (UC-08), the analytics consent (RNF-25), which the back office never shows, and the
 * message after the customer deleted its account (UC-28) and the WhatsApp button (T-E7-27). The account, checkout, tracking and search pages are kept out of search engines (T-E6-23).
 */
export function StoreLayout() {
  const { pathname } = useLocation();
  useFocusPageOnNavigation(pathname);
  return (
    <CartPanelProvider>
      {isPrivatePath(pathname) && <NoIndex />}
      <SkipLink />
      <SiteHeader />
      {/* Before the page, so a returning visitor's tools are loaded when the page sends its first events. */}
      <Analytics />
      <AccountDeletedNotice />
      {/* A new path is a new page, which enters; a new query on the same page (filters, sort) is not. */}
      <PageEntrance key={pathname}>
        <Outlet />
      </PageEntrance>
      {pathname === '/' && <BenefitsBar />}
      <SiteFooter />
      <WhatsAppButton />
      <CartPanelOnDemand />
    </CartPanelProvider>
  );
}
