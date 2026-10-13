import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { EMPTY_CART, type Cart } from '../cart/cartApi.ts';
import { CART_KEY } from '../cart/useCart.ts';
import { fetchShippingCost, placeOrder } from './checkoutApi.ts';
import type { Province } from './checkoutForm.ts';
import { keepTrackingToken } from './trackingTokenStore.ts';

/**
 * The step-1 summary for a province (UC-13). The cart's amounts are part of the key, so a change to the cart (an
 * item, the coupon) reads it again: free shipping depends on the subtotal after the discount.
 */
export function useShippingCost(province: Province | '', cart: Cart | undefined) {
  return useQuery({
    queryKey: ['checkout', 'shipping-cost', province, cart?.subtotalCents, cart?.discountCents] as const,
    queryFn: ({ signal }) => fetchShippingCost(province as Province, signal),
    enabled: province !== '' && cart !== undefined,
  });
}

/**
 * POST /api/checkout. A placed order keeps its token for the return from the provider; a refusal about the cart
 * reads it again, so the page shows why.
 */
export function usePlaceOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, body }: { key: string; body: Parameters<typeof placeOrder>[1] }) => placeOrder(key, body),
    onSuccess: (answer) => {
      switch (answer.status) {
        case 'placed':
          keepTrackingToken(answer.order.trackingToken);
          break;
        case 'emptyCart':
        case 'cartNotReady':
        case 'couponNotApplicable':
          void queryClient.invalidateQueries({ queryKey: CART_KEY });
          break;
        case 'noShipping':
          void queryClient.invalidateQueries({ queryKey: ['checkout', 'shipping-cost'] });
          break;
        default:
          break;
      }
    },
  });
}

/**
 * The placed order emptied the cart on the server. The checkout page calls this once it shows that it is leaving,
 * so the empty cart never replaces the page before the redirect.
 */
export function useEmptiedCart() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.setQueryData<Cart>(CART_KEY, EMPTY_CART);
  };
}
