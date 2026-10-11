import { useInfiniteQuery } from '@tanstack/react-query';
import { fetchProductPage } from './catalogApi.ts';
import type { ListingCriteria } from './listingCriteria.ts';

/**
 * The pages of the listing loaded so far; "Show more" fetches the next one while the API says there are more. New
 * filters or a new sort are a new query, so the listing starts again from page 0.
 */
export function useProductListing(category: string | undefined, criteria: ListingCriteria) {
  return useInfiniteQuery({
    queryKey: ['catalog', 'products', category ?? null, criteria],
    queryFn: ({ pageParam, signal }) => fetchProductPage(category, criteria, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (lastPage, pages) =>
      lastPage.status === 'listed' && lastPage.hasMore ? pages.length : undefined,
  });
}
