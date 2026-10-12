import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { addToCart, fetchCart } from './cartApi.ts';
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
