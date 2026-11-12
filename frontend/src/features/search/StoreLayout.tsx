import { useEffect, useLayoutEffect, useRef } from 'react';
import { NavigationType, Outlet, useLocation, useNavigationType } from 'react-router';
import { es } from '../../i18n/es.ts';
import { PageEntrance } from '../../components/motion/PageEntrance.tsx';
import { Analytics } from '../analytics/ConsentBanner.tsx';
import { AccountDeletedNotice } from '../account/AccountDeletedNotice.tsx';
import { SignOutProvider } from '../account/signOutFlow.tsx';
import { WelcomeProvider } from '../account/welcome.tsx';
import { isAccountAreaPath } from '../account/AccountLink.tsx';
import { CartPanelOnDemand } from '../cart/CartPanelOnDemand.tsx';
import { CartPanelProvider } from '../cart/CartPanelContext.tsx';
import { WhatsAppButton } from '../help/WhatsAppButton.tsx';
import { NoIndex } from '../seo/NoIndex.tsx';
import { isPrivatePath } from '../seo/privatePaths.ts';
import { BenefitsBar } from './BenefitsBar.tsx';
import { SiteFooter } from './SiteFooter.tsx';
import { SiteHeader } from './SiteHeader.tsx';
import './search.css';

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

/**
 * WCAG 2.4.3: after moving to another page (not on the first one), focus starts at the page, not at <body>. A page
 * that already put the focus on something of its own (a notice it shows on arrival, as "Datos y acceso" does after a
 * provider's return, T-E7-62) keeps it: its effect runs before this one.
 */
function useFocusPageOnNavigation(pathname: string) {
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    const focused = document.activeElement;
    if (focused instanceof HTMLElement && focused !== document.body && focused.closest('main') !== null) {
      return;
    }
    focusPage();
  }, [pathname]);
}

/**
 * A link to another page opens it at the top, not where the previous page was scrolled to. Going back or forward (POP)
 * is left to the browser, a query change on the same page (filters, sort) does not move the page, and a link with a
 * hash (`/#contactanos`) scrolls to its target instead.
 */
function useScrollToTopOnNavigation(pathname: string, hash: string) {
  const type = useNavigationType();
  const first = useRef(true);
  // A layout effect, so it runs before the effects of the page itself: a page that scrolls to a place of its own (the
  // home page to its listing, UC-31) does it after this one and keeps it.
  useLayoutEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    if (type !== NavigationType.Pop && hash === '') {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    }
    // Only a new path counts: `hash` and `type` are read as they are at that moment.
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
  const { pathname, hash } = useLocation();
  useFocusPageOnNavigation(pathname);
  useScrollToTopOnNavigation(pathname, hash);
  return (
    <CartPanelProvider>
      {/* T-E7-66: every "Salir" of the header, the phone menu and the account area goes through it. */}
      <SignOutProvider>
        {/* T-E7-72: the welcome under the header once the customer signs in. */}
        <WelcomeProvider>
          {isPrivatePath(pathname) && <NoIndex />}
          <SkipLink />
          <SiteHeader />
          {/* Before the page, so a returning visitor's tools are loaded when the page sends its first events. */}
          <Analytics />
          <AccountDeletedNotice />
          {/* A new path is a new page, which enters; a new query on the same page (filters, sort) is not. The account
              area's pages share one entry: the area stays and enters its own content (T-E7-60). */}
          <PageEntrance key={isAccountAreaPath(pathname) ? 'account-area' : pathname}>
            <Outlet />
          </PageEntrance>
          {pathname === '/' && <BenefitsBar />}
          <SiteFooter />
          <WhatsAppButton />
          <CartPanelOnDemand />
        </WelcomeProvider>
      </SignOutProvider>
    </CartPanelProvider>
  );
}
