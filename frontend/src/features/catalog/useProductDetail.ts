import { useQuery } from '@tanstack/react-query';
import { fetchProductDetail } from './productDetailApi.ts';

/** The product of the detail page with its variants and available stock (UC-03). */
export function useProductDetail(slug: string) {
  return useQuery({
    queryKey: ['catalog', 'product', slug],
    queryFn: ({ signal }) => fetchProductDetail(slug, signal),
  });
}
