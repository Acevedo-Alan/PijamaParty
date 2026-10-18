import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn } from './adminSession.ts';

type ReviewResponse = components['schemas']['ModeratedReviewResponse'];

export type ReviewStatus = NonNullable<ReviewResponse['status']>;

/** The moderation tabs, pending first (UC-25). */
export const REVIEW_STATUSES: readonly ReviewStatus[] = ['PENDING', 'APPROVED', 'REJECTED'];

/** A review as the back office moderates it (UC-25), with its product. */
export interface ModeratedReview {
  id: string;
  /** Name and slug are null when the catalog no longer has the product. */
  product: { id: string; name: string | null; slug: string | null };
  authorName: string;
  rating: number;
  text: string | null;
  status: ReviewStatus;
  /** The last submission. */
  updatedAt: Date;
}

export type ModerationAnswer = { status: 'done'; review: ModeratedReview } | { status: 'notFound' };

export type DeleteReviewAnswer = { status: 'deleted' } | { status: 'notFound' };

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

function toReview(row: ReviewResponse): ModeratedReview {
  const { id, product, authorName, rating, status, updatedAt } = row;
  if (
    id === undefined ||
    product?.id === undefined ||
    authorName === undefined ||
    rating === undefined ||
    status === undefined ||
    updatedAt === undefined
  ) {
    throw new Error('Incomplete review');
  }
  return {
    id,
    product: {
      id: product.id,
      name: product.name ?? null,
      slug: product.slug ?? null,
    },
    authorName,
    rating,
    text: row.text ?? null,
    status,
    updatedAt: new Date(updatedAt),
  };
}

/** One page of the reviews in {@code status}: pending ones oldest first, the others newest first. */
export async function fetchReviews(
  status: ReviewStatus,
  page: number,
  signal?: AbortSignal,
): Promise<{ items: ModeratedReview[]; hasMore: boolean }> {
  const { data, response } = await api.GET('/api/admin/reviews', {
    params: { query: { status, page } },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (data === undefined) {
    throw failed('Review list', response);
  }
  return {
    items: (data.items ?? []).map(toReview),
    hasMore: data.hasMore === true,
  };
}

/** Approves or rejects a review; repeating a decision changes nothing. */
export async function moderateReview(id: string, decision: 'approve' | 'reject'): Promise<ModerationAnswer> {
  const { data, response } =
    decision === 'approve'
      ? await api.POST('/api/admin/reviews/{id}/approve', {
          params: { path: { id } },
        })
      : await api.POST('/api/admin/reviews/{id}/reject', {
          params: { path: { id } },
        });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (data === undefined) {
    throw failed('Review moderation', response);
  }
  return { status: 'done', review: toReview(data) };
}

export async function deleteReview(id: string): Promise<DeleteReviewAnswer> {
  const { response } = await api.DELETE('/api/admin/reviews/{id}', {
    params: { path: { id } },
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (!response.ok) {
    throw failed('Review deletion', response);
  }
  return { status: 'deleted' };
}
