import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { problemProperty } from '../admin/adminSession.ts';

type MyReviewResponse = components['schemas']['MyReviewResponse'];

/** The query key of the customer's reviews; signing out forgets them. */
export const MY_REVIEWS_KEY = ['account', 'reviews'] as const;

export const myReviewKey = (productId: string) => [...MY_REVIEWS_KEY, productId] as const;

export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** The customer's own review of a product, with its moderation status (UC-24, T-E5-06). */
export interface MyReview {
  rating: number;
  text: string | null;
  status: ReviewStatus;
  /** The last submission. */
  updatedAt: string;
}

/** GET /api/account/reviews/{productId}: null when nobody is signed in as a customer. */
export type MyReviewStanding = { eligible: boolean; review: MyReview | null } | null;

/** The fields of INVALID_REVIEW, in the request's own names. */
export type ReviewField = 'rating' | 'text';

const REVIEW_FIELDS: readonly ReviewField[] = ['rating', 'text'];

/** PUT of the customer's review. */
export type SubmitReviewAnswer =
  | { status: 'saved'; review: MyReview }
  | { status: 'invalid'; fields: ReviewField[] }
  | { status: 'notABuyer' }
  | { status: 'notFound' }
  | { status: 'signedOut' };

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

function isStatus(value: unknown): value is ReviewStatus {
  return value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED';
}

function toReview(data: MyReviewResponse | undefined): MyReview | null {
  if (data?.rating === undefined || !isStatus(data.status) || data.updatedAt === undefined) {
    return null;
  }
  return { rating: data.rating, text: data.text ?? null, status: data.status, updatedAt: data.updatedAt };
}

export async function fetchMyReview(productId: string, signal?: AbortSignal): Promise<MyReviewStanding> {
  const { data, response } = await api.GET('/api/account/reviews/{productId}', {
    params: { path: { productId } },
    signal: signal ?? null,
  });
  if (response.status === 401 || response.status === 403) {
    return null;
  }
  if (response.status !== 200 || data?.eligible === undefined) {
    throw failed('Reading my review', response);
  }
  return { eligible: data.eligible, review: toStoredReview(response, data.review) };
}

/** The server sends null without a review; the generated schema drops null from the reference. */
function toStoredReview(response: Response, sent: MyReviewResponse | null | undefined): MyReview | null {
  if (sent === undefined || sent === null) {
    return null;
  }
  const review = toReview(sent);
  if (review === null) {
    throw failed('Reading my review', response);
  }
  return review;
}

/** Creates the customer's review of the product or replaces it; either way it waits for moderation (UC-25). */
export async function submitReview(productId: string, rating: number, text: string): Promise<SubmitReviewAnswer> {
  const { data, error, response } = await api.PUT('/api/account/reviews/{productId}', {
    params: { path: { productId } },
    body: { rating, text },
  });
  switch (response.status) {
    case 200:
    case 201: {
      const review = toReview(data);
      if (review === null) {
        throw failed('Submitting a review', response);
      }
      return { status: 'saved', review };
    }
    case 401:
      return { status: 'signedOut' };
    case 404:
      return { status: 'notFound' };
    case 422: {
      const problem = problemProperty(error, 'problem');
      if (problem === 'NOT_A_BUYER') {
        return { status: 'notABuyer' };
      }
      const names = problemProperty(error, 'fields');
      if (problem !== 'INVALID_REVIEW' || !Array.isArray(names)) {
        throw failed('Submitting a review', response);
      }
      return { status: 'invalid', fields: REVIEW_FIELDS.filter((field) => names.includes(field)) };
    }
    default:
      throw failed('Submitting a review', response);
  }
}
