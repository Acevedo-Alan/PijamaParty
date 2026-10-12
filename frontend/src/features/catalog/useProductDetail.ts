import { useQuery } from '@tanstack/react-query';
import { fetchProductDetail } from './productDetailApi.ts';

export const productDetailKey = (slug: string) => ['catalog', 'product', slug] as const;

/** The product of the detail page with its variants and available stock (UC-03). */
export function useProductDetail(slug: string) {
  return useQuery({
    queryKey: productDetailKey(slug),
    queryFn: ({ signal }) => fetchProductDetail(slug, signal),
  });
}
