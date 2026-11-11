import { useEffect, useRef, type ReactNode } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { PageEntrance } from '../../components/motion/PageEntrance.tsx';
import { es } from '../../i18n/es.ts';
import { keepInSight } from '../../components/keepInSight.ts';
import type { Customer } from './accountApi.ts';
import { ACCOUNT_PATH, ADDRESSES_PATH, DATA_PATH, ORDERS_PATH, useSignOut } from './AccountLink.tsx';
import { useCustomer } from './useCustomer.ts';
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

/** Outline drawings in the header's style: a house, a parcel, a pin, a person's card and a door. */
const icons = {
  summary: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 11 12 4.5l8 6.5M6.5 9.5V19h11V9.5" />
    </svg>
  ),
  orders: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 8 12 4l8 4v8l-8 4-8-4V8Zm0 0 8 4 8-4M12 12v8" />
    </svg>
  ),
  addresses: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z" />
      <circle cx="12" cy="10" r="2.3" />
    </svg>
  ),
  data: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3.5" y="5" width="17" height="14" rx="2" />
      <circle cx="9" cy="11" r="2" />
      <path d="M6 16c.6-1.6 1.7-2.4 3-2.4s2.4.8 3 2.4M14.5 10h3M14.5 13h3" />
    </svg>
  ),
  signOut: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M10 5H6v14h4M14 8l4 4-4 4M18 12H9" />
    </svg>
  ),
};

function AreaLink({ to, end = false, icon, children }: { to: string; end?: boolean; icon: ReactNode; children: string }) {
  return (
    <li>
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
  const exit = useSignOut();
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
    <nav className="account-nav" aria-label={messages.navigation}>
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
            className="account-nav-link"
            disabled={exit.isPending}
            onClick={() => {
              exit.mutate();
            }}
          >
            {icons.signOut}
            {es.account.signOut}
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
  // The navigation sits beside the page's main content, not in it: <main> holds only the page. The area stays when
  // the section changes (StoreLayout keys it once for all its paths); only the new section enters.
  return (
    <div className="wrap account-area">
      {customer !== null && <AccountNav customer={customer} />}
      <main className="account-area-content">
        <PageEntrance key={pathname}>
          <Outlet />
        </PageEntrance>
      </main>
    </div>
  );
}
