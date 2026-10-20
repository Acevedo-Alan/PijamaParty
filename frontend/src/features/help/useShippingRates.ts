import { useQuery } from '@tanstack/react-query';
import { fetchShippingRates } from './shippingRatesApi.ts';

const SHIPPING_RATES_KEY = ['shipping', 'rates'] as const;

/** The public shipping costs (RNF-31.6), shared by the FAQ, the product page and the header strip (UC-31). */
export function useShippingRates() {
  return useQuery({
    queryKey: SHIPPING_RATES_KEY,
    queryFn: ({ signal }) => fetchShippingRates(signal),
    staleTime: 5 * 60 * 1000,
  });
}
