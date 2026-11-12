import { useEffect, useRef, type ReactNode } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { PageEntrance } from '../../components/motion/PageEntrance.tsx';
import { es } from '../../i18n/es.ts';
import { keepInSight } from '../../components/keepInSight.ts';
import type { Customer } from './accountApi.ts';
import { ACCOUNT_PATH, ADDRESSES_PATH, DATA_PATH, ORDERS_PATH } from './paths.ts';
import { isLeaving, isSigningOut, useSignOutFlow } from './signOutFlow.tsx';
import { accountNavIcons } from './accountNavIcons.tsx';
import { useCustomer } from './useCustomer.ts';
// T-E7-63: the account's own type, fetched with this chunk only; account.css sets it on .account-area.
import '@fontsource/poppins/500.css';
import '@fontsource/poppins/600.css';
import '@fontsource/open-sans/400.css';
import '@fontsource/open-sans/600.css';
import '@fontsource/open-sans/700.css';
import './account.css';

const messages = es.account.area;

/** The first letters of the name's first two words, in upper case: "Cliente Prueba" is "CP". */
export function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word.charAt(0))
    .join('')
    .toLocaleUpperCase('es-AR');
}

const icons = accountNavIcons;

function AreaLink({ to, end = false, icon, children }: { to: string; end?: boolean; icon: ReactNode; children: string }) {
  // T-E7-66: while signing out the sections fade and stop answering; "Salir" keeps the focus.
  const busy = isSigningOut(useSignOutFlow().phase);
  return (
    <li inert={busy}>
      <NavLink to={to} end={end} className="account-nav-link">
        {icon}
        {children}
      </NavLink>
    </li>
  );
}

/**
 * T-E7-60: the account area's navigation. On a computer a sidebar with the customer's initials, name and email, the
 * four sections (the current one marked: NavLink sets aria-current="page"; an order's detail marks "Mis pedidos") and
 * "Salir"; on a phone the same links as a row of pills that scrolls sideways (account.css).
 */
function AccountNav({ customer }: { customer: Customer | undefined }) {
  const { phase, start } = useSignOutFlow();
  const busy = isSigningOut(phase);
  const links = useRef<HTMLUListElement>(null);
  const { pathname } = useLocation();

  // On a phone the row may start with the current pill out of sight (Datos y acceso is the last one): bring it in,
  // scrolling the row only, never the page.
  useEffect(() => {
    const list = links.current;
    const current = list?.querySelector<HTMLElement>('[aria-current="page"]');
    if (list !== null && current !== null && current !== undefined) {
      keepInSight(list, current);
    }
  }, [pathname]);

  return (
    <nav className="account-nav" aria-label={messages.navigation} data-signing-out={busy ? '' : undefined}>
      {/* Always there, so the sidebar does not grow when the customer arrives. */}
      <div className="account-nav-who">
        {customer !== undefined && (
          <>
            <span className="account-initials" aria-hidden="true">
              {initials(customer.name)}
            </span>
            <p className="account-nav-name">{customer.name}</p>
            <p className="account-nav-email">{customer.email}</p>
          </>
        )}
      </div>
      <ul className="account-nav-links" ref={links}>
        <AreaLink to={ACCOUNT_PATH} end icon={icons.summary}>
          {messages.summary}
        </AreaLink>
        <AreaLink to={ORDERS_PATH} icon={icons.orders}>
          {messages.orders}
        </AreaLink>
        <AreaLink to={ADDRESSES_PATH} icon={icons.addresses}>
          {messages.addresses}
        </AreaLink>
        <AreaLink to={DATA_PATH} icon={icons.data}>
          {messages.data}
        </AreaLink>
        <li className="account-nav-exit">
          <button
            type="button"
            className="account-nav-link account-nav-sign-out"
            aria-disabled={busy || undefined}
            onClick={start}
          >
            {icons.signOut}
            {busy ? es.account.signingOut : es.account.signOut}
          </button>
        </li>
      </ul>
    </nav>
  );
}

/**
 * T-E7-60: the layout of the account pages (the summary, "Mis pedidos" and an order's detail, "Mis direcciones" and
 * "Datos y acceso"): the navigation, then the page. Each page still sends a visitor who is not signed in to sign in,
 * and the deleted account home: the area only waits for the customer to show who it is.
 */
export function AccountArea() {
  const customer = useCustomer();
  const { pathname } = useLocation();
  // T-E7-66: once the sign-out answers, the page fades out before the store goes home.
  const leaving = isLeaving(useSignOutFlow().phase);
  // The navigation sits beside the page's main content, not in it: <main> holds only the page. The area stays when
  // the section changes (StoreLayout keys it once for all its paths); only the new section enters.
  return (
    // An order's detail has its own way back (T-E7-64): on a phone it shows in place of the pills.
    <div className="wrap account-area" data-order-detail={pathname.startsWith(`${ORDERS_PATH}/`) ? '' : undefined}>
      {customer !== null && <AccountNav customer={customer} />}
      <main className="account-area-content" data-leaving={leaving ? '' : undefined}>
        <PageEntrance key={pathname}>
          <Outlet />
        </PageEntrance>
      </main>
    </div>
  );
}
