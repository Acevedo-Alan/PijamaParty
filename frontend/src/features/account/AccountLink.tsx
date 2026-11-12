import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import type { Customer } from './accountApi.ts';
import { accountNavIcons } from './accountNavIcons.tsx';
import { ACCOUNT_PATH, ADDRESSES_PATH, DATA_PATH, ORDERS_PATH } from './paths.ts';
import { isLeaving, isSigningOut, useSignOutFlow } from './signOutFlow.tsx';
import { firstName, initials, useCustomer } from './useCustomer.ts';
import './account.css';

export { ACCOUNT_PATH, ADDRESSES_PATH, DATA_PATH, isAccountAreaPath, ORDERS_PATH } from './paths.ts';
export const SIGN_IN_PATH = '/cuenta/ingresar';
export const REGISTER_PATH = '/cuenta/registro';
export const RECOVER_PATH = '/cuenta/recuperar';

/** The page of the verification link; the token comes in the fragment, which never reaches a server. */
export const VERIFY_PATH = '/cuenta/verificar';
/** The page of the reset link; the token comes in the fragment, which never reaches a server. */
export const RESET_PATH = '/cuenta/restablecer';
/** The page of the link confirmation link; the token comes in the fragment, which never reaches a server. */
export const LINK_PATH = '/cuenta/vincular';
/** Where the provider's return sends a visitor whose Facebook account gave no email. */
export const COMPLETE_EMAIL_PATH = '/cuenta/completar-email';

interface ChooseProps {
  /** A link or "Salir" was chosen: the list or menu that holds them closes. */
  onChoose?: () => void;
}

/**
 * The account's four sections, named and drawn as the account area's navigation names and draws them (T-E7-60), so
 * the header's list and the phone menu lead to the same places by the same names (T-E7-67): "Resumen" (UC-20, UC-22),
 * "Mis pedidos" (UC-22), "Mis direcciones" (UC-20) and "Datos y acceso" (UC-27, UC-28, UC-40).
 */
const SECTIONS = [
  { to: ACCOUNT_PATH, icon: accountNavIcons.summary, label: es.account.area.summary },
  { to: ORDERS_PATH, icon: accountNavIcons.orders, label: es.account.area.orders },
  { to: ADDRESSES_PATH, icon: accountNavIcons.addresses, label: es.account.area.addresses },
  { to: DATA_PATH, icon: accountNavIcons.data, label: es.account.area.data },
] as const;

/**
 * "Salir" (UC-19) through the store's sign-out flow (T-E7-66): while the request goes it says "Cerrando sesión…",
 * keeps the focus and a second press does nothing.
 */
function SignOutButton({ className }: { className: string }) {
  const { phase, start } = useSignOutFlow();
  const busy = isSigningOut(phase);
  return (
    <button type="button" className={className} aria-disabled={busy || undefined} onClick={start}>
      {accountNavIcons.signOut}
      {busy ? es.account.signingOut : es.account.signOut}
    </button>
  );
}

/**
 * The header's list: the greeting with the account's email (T-E7-65), the four sections and "Salir", each with the
 * outline icon of the account area's sidebar. While signing out (T-E7-66) the sections fade and stop answering.
 */
function SignedInLinks({ customer, onChoose }: ChooseProps & { customer: Customer }) {
  const busy = isSigningOut(useSignOutFlow().phase);

  return (
    <>
      <div className="account-greeting">
        <p>{es.account.greeting(firstName(customer.name))}</p>
        <p className="account-greeting-email">{customer.email}</p>
      </div>
      <ul className="account-links" data-signing-out={busy ? '' : undefined}>
        {SECTIONS.map((section) => (
          <li key={section.to} inert={busy}>
            <Link to={section.to} onClick={onChoose}>
              {section.icon}
              {section.label}
            </Link>
          </li>
        ))}
        <li className="account-links-exit">
          <SignOutButton className="account-sign-out" />
        </li>
      </ul>
    </>
  );
}

/** A person's outline, drawn like the cart's bag (T-E7-23); the phone menu's account shortcut carries it too. */
export function PersonIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8" r="4" />
      <path d="M4.5 20c.8-3.5 3.8-5.5 7.5-5.5s6.7 2 7.5 5.5" strokeLinecap="round" />
    </svg>
  );
}

/**
 * The header's account icon at every width (UC-31, T-E7-23, T-E7-39): a link to the sign-in page when signed out,
 * which offers "Crear cuenta" too; with a session, "Mi cuenta", which opens the list of the account links. Escape, a
 * click outside or a chosen link closes it.
 */
export function HeaderAccount() {
  const customer = useCustomer();
  if (customer === undefined) {
    return null;
  }
  return (
    <div className="header-account">
      {customer === null ? (
        <Link to={SIGN_IN_PATH} className="icon-btn account-icon" aria-label={es.account.signInLink}>
          <PersonIcon />
        </Link>
      ) : (
        <AccountMenu customer={customer} />
      )}
    </div>
  );
}

function AccountMenu({ customer }: { customer: Customer }) {
  const [isOpen, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const listId = useId();
  // T-E7-66: once the sign-out answers, the list closes upwards and the icon loses its dot and its shade (account.css).
  const leaving = isLeaving(useSignOutFlow().phase);
  const letters = initials(customer.name);

  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }
    function closeOutside(event: PointerEvent) {
      if (event.target instanceof Node && root.current?.contains(event.target) !== true) {
        setOpen(false);
      }
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        button.current?.focus();
      }
    }
    document.addEventListener('pointerdown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [isOpen]);

  return (
    <div className="account-menu" ref={root}>
      <button
        ref={button}
        type="button"
        className="icon-btn account-icon"
        data-signed-in=""
        data-initials={letters === '' ? undefined : ''}
        data-leaving={leaving ? '' : undefined}
        aria-label={es.account.menu}
        aria-expanded={isOpen && !leaving}
        aria-controls={listId}
        onClick={() => {
          setOpen(!isOpen);
        }}
      >
        {/* T-E7-72: the initials say at a glance that the customer is in; a name without letters keeps the icon. */}
        {letters === '' ? (
          <PersonIcon />
        ) : (
          <span className="account-icon-initials" aria-hidden="true">
            {letters}
          </span>
        )}
      </button>
      {isOpen && (
        <div id={listId} className="account-menu-list" data-leaving={leaving ? '' : undefined}>
          <SignedInLinks
            customer={customer}
            onChoose={() => {
              setOpen(false);
            }}
          />
        </div>
      )}
    </div>
  );
}

/**
 * The account level of the phone menu (UC-31, T-E7-39, T-E7-67): a stitched profile card (the initials of the account
 * area in a circle, the greeting and the email), the four sections as full-width rows with a chevron, and "Salir" apart
 * at the bottom of the level, in a soft red. Once the sign-out answers, the menu closes, so the goodbye under the header
 * shows (T-E7-66).
 */
export function MenuAccountLinks({ customer, onChoose }: Required<ChooseProps> & { customer: Customer }) {
  const { phase } = useSignOutFlow();
  const busy = isSigningOut(phase);
  const leaving = isLeaving(phase);
  useEffect(() => {
    if (leaving) {
      onChoose();
    }
  }, [leaving, onChoose]);
  return (
    <div className="menu-account">
      <div className="menu-account-profile">
        <span className="menu-account-initials" aria-hidden="true">
          {initials(customer.name)}
        </span>
        <p className="menu-account-greeting">{es.account.greeting(firstName(customer.name))}</p>
        <p className="menu-account-email">{customer.email}</p>
      </div>
      <ul className="menu-account-links" data-signing-out={busy ? '' : undefined}>
        {SECTIONS.map((section) => (
          <li key={section.to} inert={busy}>
            <Link to={section.to} onClick={onChoose}>
              {section.icon}
              <span>{section.label}</span>
              {accountNavIcons.chevron}
            </Link>
          </li>
        ))}
      </ul>
      <div className="menu-account-exit">
        <SignOutButton className="menu-account-sign-out" />
      </div>
    </div>
  );
}
