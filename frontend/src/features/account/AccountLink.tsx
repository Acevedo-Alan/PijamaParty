import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { signOut } from './accountApi.ts';
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

/**
 * The header's account entry (UC-19): "Ingresar" when signed out; the greeting, "Mis pedidos" (UC-22), "Mis
 * direcciones" (UC-20), "Mi cuenta" (UC-27) and "Salir" when signed in.
 */
export function AccountLink() {
  const customer = useCustomer();
  const queryClient = useQueryClient();
  const exit = useMutation({
    mutationFn: signOut,
    onSettled: () => {
      forgetAccount(queryClient);
    },
  });

  if (customer === undefined) {
    return null;
  }
  if (customer === null) {
    return (
      <Link to={SIGN_IN_PATH} className="account-link">
        {es.account.signInLink}
      </Link>
    );
  }
  return (
    <div className="account-link">
      <span className="account-greeting">{es.account.greeting(firstName(customer.name))}</span>
      <Link to={ORDERS_PATH} className="account-orders">
        {es.account.ordersLink}
      </Link>
      <Link to={ADDRESSES_PATH} className="account-addresses">
        {es.account.addressesLink}
      </Link>
      <Link to={ACCOUNT_PATH} className="account-mine">
        {es.account.myAccountLink}
      </Link>
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
    </div>
  );
}
