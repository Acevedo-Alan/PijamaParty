import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { CART_KEY } from '../cart/useCart.ts';
import { signOut } from './accountApi.ts';
import { ADDRESSES_KEY } from './addressBookApi.ts';
import { CUSTOMER_KEY, firstName, useCustomer } from './useCustomer.ts';
import './account.css';

export const SIGN_IN_PATH = '/cuenta/ingresar';
export const REGISTER_PATH = '/cuenta/registro';
export const RECOVER_PATH = '/cuenta/recuperar';
export const ADDRESSES_PATH = '/cuenta/direcciones';

/**
 * The header's account entry (UC-19): "Ingresar" when signed out; the greeting, "Mis direcciones" (UC-20) and
 * "Salir" when signed in.
 */
export function AccountLink() {
  const customer = useCustomer();
  const queryClient = useQueryClient();
  const exit = useMutation({
    mutationFn: signOut,
    onSettled: () => {
      queryClient.setQueryData(CUSTOMER_KEY, null);
      queryClient.removeQueries({ queryKey: ADDRESSES_KEY });
      // The cart shown belonged to the account: the guest's comes back with its cookie.
      void queryClient.invalidateQueries({ queryKey: CART_KEY });
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
      <Link to={ADDRESSES_PATH} className="account-addresses">
        {es.account.addressesLink}
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
