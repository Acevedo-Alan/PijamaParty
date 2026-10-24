import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { signOut, type Customer } from './accountApi.ts';
import { forgetAccount } from './forgetAccount.ts';
import { firstName, useCustomer } from './useCustomer.ts';
import './account.css';

export const SIGN_IN_PATH = '/cuenta/ingresar';
export const REGISTER_PATH = '/cuenta/registro';
export const RECOVER_PATH = '/cuenta/recuperar';
export const ADDRESSES_PATH = '/cuenta/direcciones';
export const ORDERS_PATH = '/cuenta/pedidos';
export const ACCOUNT_PATH = '/cuenta';
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

/** "Ingresar" and "Crear cuenta" (UC-19, UC-31); returning customers sign in more often, so "Ingresar" is filled. */
function SignedOutLinks({ onChoose }: ChooseProps) {
  return (
    <>
      <Link to={SIGN_IN_PATH} className="account-btn primary" onClick={onChoose}>
        {es.account.signInLink}
      </Link>
      <Link to={REGISTER_PATH} className="account-btn secondary" onClick={onChoose}>
        {es.account.registerLink}
      </Link>
    </>
  );
}

/** The greeting, "Mis pedidos" (UC-22), "Mis direcciones" (UC-20), "Mi cuenta" (UC-27) and "Salir" (UC-19). */
function SignedInLinks({ customer, onChoose }: ChooseProps & { customer: Customer }) {
  const queryClient = useQueryClient();
  const exit = useMutation({
    mutationFn: signOut,
    onSettled: () => {
      forgetAccount(queryClient);
    },
  });

  return (
    <>
      <p className="account-greeting">{es.account.greeting(firstName(customer.name))}</p>
      <ul className="account-links">
        <li>
          <Link to={ORDERS_PATH} onClick={onChoose}>
            {es.account.ordersLink}
          </Link>
        </li>
        <li>
          <Link to={ADDRESSES_PATH} onClick={onChoose}>
            {es.account.addressesLink}
          </Link>
        </li>
        <li>
          <Link to={ACCOUNT_PATH} onClick={onChoose}>
            {es.account.myAccountLink}
          </Link>
        </li>
        <li>
          <button
            type="button"
            className="account-sign-out"
            disabled={exit.isPending}
            onClick={() => {
              exit.mutate();
            }}
          >
            {es.account.signOut}
          </button>
        </li>
      </ul>
    </>
  );
}

/**
 * The header's account entry from 860 px (UC-31): "Ingresar" and "Crear cuenta" when signed out; with a session,
 * "Mi cuenta", which opens the list of the account links. Escape, a click outside or a chosen link closes it.
 */
export function HeaderAccount() {
  const customer = useCustomer();
  if (customer === undefined) {
    return null;
  }
  return (
    <div className="header-account">
      {customer === null ? <SignedOutLinks /> : <AccountMenu customer={customer} />}
    </div>
  );
}

function AccountMenu({ customer }: { customer: Customer }) {
  const [isOpen, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const listId = useId();

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
        className="account-btn secondary account-menu-btn"
        aria-expanded={isOpen}
        aria-controls={listId}
        onClick={() => {
          setOpen(!isOpen);
        }}
      >
        {es.account.menu}
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M7 10l5 5 5-5" />
        </svg>
      </button>
      {isOpen && (
        <div id={listId} className="account-menu-list">
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

/** The account entries of the phone menu (UC-31): the same as the header's, with the signed-in links open. */
export function MenuAccount({ onChoose }: Required<ChooseProps>) {
  const customer = useCustomer();
  if (customer === undefined) {
    return null;
  }
  return (
    <div className="menu-account">
      {customer === null ? (
        <SignedOutLinks onChoose={onChoose} />
      ) : (
        <SignedInLinks customer={customer} onChoose={onChoose} />
      )}
    </div>
  );
}
