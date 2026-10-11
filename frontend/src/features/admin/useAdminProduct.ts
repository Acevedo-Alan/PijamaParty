import { useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchAdminProduct, type AdminProduct, type ProductAnswer } from './productsApi.ts';

export function productKey(id: string) {
  return ['admin', 'product', id] as const;
}

/** A product with its variants and photos, as the back office edits it (UC-06). */
export function useAdminProduct(id: string) {
  return useQuery({ queryKey: productKey(id), queryFn: ({ signal }) => fetchAdminProduct(id, signal) });
}

/** Keeps the product the server answered after a change, and marks the list stale. */
export function useStoreProduct() {
  const queryClient = useQueryClient();
  return (product: AdminProduct) => {
    const found: ProductAnswer = { status: 'found', product };
    queryClient.setQueryData(productKey(product.id), found);
    void queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
  };
}
