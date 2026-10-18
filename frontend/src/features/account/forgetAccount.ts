import type { QueryClient } from '@tanstack/react-query';
import { CART_KEY } from '../cart/useCart.ts';
import { MY_REVIEWS_KEY } from '../reviews/myReviewApi.ts';
import { ADDRESSES_KEY } from './addressBookApi.ts';
import { MY_ORDERS_KEY } from './myOrdersApi.ts';
import { SIGN_IN_METHODS_KEY } from './signInMethodsApi.ts';
import { CUSTOMER_KEY } from './useCustomer.ts';

/**
 * After signing out (UC-19) or deleting the account (UC-28): nobody is signed in, and nothing of the account stays
 * in the cache. The cart shown belonged to the account: the guest's comes back with its cookie.
 */
export function forgetAccount(queryClient: QueryClient): void {
  queryClient.setQueryData(CUSTOMER_KEY, null);
  queryClient.removeQueries({ queryKey: ADDRESSES_KEY });
  queryClient.removeQueries({ queryKey: MY_ORDERS_KEY });
  queryClient.removeQueries({ queryKey: SIGN_IN_METHODS_KEY });
  queryClient.removeQueries({ queryKey: MY_REVIEWS_KEY });
  void queryClient.invalidateQueries({ queryKey: CART_KEY });
}
