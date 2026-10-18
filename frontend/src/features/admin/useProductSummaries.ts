import { useQuery } from '@tanstack/react-query';
import { fetchAllProductSummaries } from './productsApi.ts';

export const ALL_PRODUCTS_KEY = ['admin', 'products', 'all'] as const;

/** Every product, published or not, newest first (UC-06); read only when `enabled`. */
export function useProductSummaries(enabled = true) {
  return useQuery({
    queryKey: ALL_PRODUCTS_KEY,
    queryFn: ({ signal }) => fetchAllProductSummaries(signal),
    enabled,
  });
}
