import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  acknowledgePrices,
  addToCart,
  applyCoupon,
  changeQuantity,
  fetchCart,
  removeCoupon,
  removeItem,
} from './cartApi.ts';
import { useCartPanel } from './CartPanelContext.tsx';

export const CART_KEY = ['cart'] as const;

/** The visitor's cart as the server prices it (UC-08, UC-09). */
export function useCart() {
  return useQuery({ queryKey: CART_KEY, queryFn: ({ signal }) => fetchCart(signal) });
}

/** UC-08: adds units of a variant; on success the answer is the new cart and the panel shows it. */
export function useAddToCart() {
  const queryClient = useQueryClient();
  const panel = useCartPanel();
  return useMutation({
    mutationFn: ({ variantId, quantity }: { variantId: string; quantity: number }) => addToCart(variantId, quantity),
    onSuccess: (answer) => {
      if (answer.status === 'added') {
        queryClient.setQueryData(CART_KEY, answer.cart);
        panel.showAdded();
      }
    },
  });
}

/** A new quantity for an item (0 removes it), or null to remove it with DELETE. */
export type ItemChange = { quantity: number } | null;

/**
 * UC-09: changes or removes one item. The answer is the new cart; when the stock or the cart changed elsewhere the
 * cart is read again, so the panel shows the current state.
 */
export function useChangeItem(itemId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (change: ItemChange) =>
      change === null ? removeItem(itemId) : changeQuantity(itemId, change.quantity),
    onSuccess: (answer) => {
      if (answer.status === 'changed') {
        queryClient.setQueryData(CART_KEY, answer.cart);
      } else {
        void queryClient.invalidateQueries({ queryKey: CART_KEY });
      }
    },
  });
}

/** UC-09: the customer saw the changed prices; the answer is the cart without the announcements. */
export function useAcknowledgePrices() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: acknowledgePrices,
    onSuccess: (cart) => {
      queryClient.setQueryData(CART_KEY, cart);
    },
  });
}

/** UC-10: applies a code, replacing any coupon; a cart emptied elsewhere is read again. */
export function useApplyCoupon() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: applyCoupon,
    onSuccess: (answer) => {
      if (answer.status === 'applied') {
        queryClient.setQueryData(CART_KEY, answer.cart);
      } else if (answer.status === 'emptyCart') {
        void queryClient.invalidateQueries({ queryKey: CART_KEY });
      }
    },
  });
}

/** UC-10: removes the applied coupon; the answer is the cart without it. */
export function useRemoveCoupon() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: removeCoupon,
    onSuccess: (cart) => {
      queryClient.setQueryData(CART_KEY, cart);
    },
  });
}
