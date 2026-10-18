import { useInfiniteQuery } from '@tanstack/react-query';
import { fetchProductReviews, REVIEWS_PAGE_SIZE } from './productReviewsApi.ts';

export const productReviewsKey = (slug: string) => ['catalog', 'reviews', slug] as const;

/**
 * The approved reviews of the product loaded so far, with their average and count; "Ver más" fetches the next page
 * while the count says there are more. The summary near the price and the list share it.
 */
export function useProductReviews(slug: string) {
  return useInfiniteQuery({
    queryKey: productReviewsKey(slug),
    queryFn: ({ pageParam, signal }) => fetchProductReviews(slug, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, all) =>
      last !== null && all.length * REVIEWS_PAGE_SIZE < last.count
        ? all.length
        : undefined,
  });
}
