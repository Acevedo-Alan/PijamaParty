import { useQuery } from '@tanstack/react-query';
import { fetchShopCategories } from './searchApi.ts';

/** The active categories by position (UC-26), shared by the search's links and the category page's title. */
export function useShopCategories() {
  return useQuery({
    queryKey: ['catalog', 'categories'],
    queryFn: ({ signal }) => fetchShopCategories(signal),
    staleTime: 300_000,
  });
}
