import { useQuery } from '@tanstack/react-query';
import { fetchShipping } from './shippingApi.ts';

export const SHIPPING_KEY = ['admin', 'shipping'] as const;

/** The cost of each province and the free-shipping threshold (UC-12). */
export function useShipping() {
  return useQuery({
    queryKey: SHIPPING_KEY,
    queryFn: ({ signal }) => fetchShipping(signal),
  });
}
