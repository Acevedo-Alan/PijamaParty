import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';

type PublishedReviewResponse = components['schemas']['PublishedReviewResponse'];

/** An approved review as the product page shows it (UC-24, T-E5-07). */
export interface PublishedReview {
  /** The author's first name. */
  authorName: string;
  rating: number;
  text: string | null;
  createdAt: string;
}

/** One page of the approved reviews of a product, with the average and count of all of them. */
export interface ProductReviewsPage {
  /** The mean of the approved ratings with one decimal; null without approved reviews. */
  average: number | null;
  count: number;
  reviews: PublishedReview[];
}

/** The number of reviews of a page, as the API sends them. */
export const REVIEWS_PAGE_SIZE = 10;

function toReview({ authorName, rating, text, createdAt }: PublishedReviewResponse): PublishedReview {
  if (authorName === undefined || rating === undefined || createdAt === undefined) {
    throw new Error('Incomplete review');
  }
  return { authorName, rating, text: text ?? null, createdAt };
}

/**
 * GET /api/catalog/products/{slug}/reviews, newest first. A product visitors do not see has no reviews to show: the
 * product page itself answers not found.
 */
export async function fetchProductReviews(
  slug: string,
  page: number,
  signal?: AbortSignal,
): Promise<ProductReviewsPage | null> {
  const { data, response } = await api.GET('/api/catalog/products/{slug}/reviews', {
    params: { path: { slug }, query: { page } },
    signal: signal ?? null,
  });
  if (response.status === 404 || response.status === 400) {
    return null;
  }
  if (data?.count === undefined) {
    throw new Error(`Reading the reviews failed with status ${String(response.status)}`);
  }
  return { average: data.average ?? null, count: data.count, reviews: (data.reviews ?? []).map(toReview) };
}
