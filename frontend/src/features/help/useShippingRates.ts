import { useQuery } from '@tanstack/react-query';
import { fetchShippingRates } from './shippingRatesApi.ts';

const SHIPPING_RATES_KEY = ['shipping', 'rates'] as const;

/** The public shipping costs (RNF-31.6), shared by the FAQ and the product page. */
export function useShippingRates() {
  return useQuery({
    queryKey: SHIPPING_RATES_KEY,
    queryFn: ({ signal }) => fetchShippingRates(signal),
    staleTime: 5 * 60 * 1000,
  });
}
