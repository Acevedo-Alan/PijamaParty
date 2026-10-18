import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatDateTime } from '../../i18n/formatDate.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import {
  deleteReview,
  fetchReviews,
  moderateReview,
  REVIEW_STATUSES,
  type ModeratedReview,
  type ReviewStatus,
} from './reviewsApi.ts';

const messages = es.admin.reviewList;
const STATUS_PARAM = 'estado';
const MAX_STARS = 5;

/** The tab in the address, so it survives a reload; pending when absent or unknown. */
function statusOf(params: URLSearchParams): ReviewStatus {
  const value = params.get(STATUS_PARAM);
  return REVIEW_STATUSES.find((status) => status === value) ?? 'PENDING';
}

/**
 * UC-25 (T-E5-08) at /admin/resenas: the reviews by status, pending first and from the oldest submission, 50 at a
 * time, each with its product, author, rating and text, to approve, reject or delete (after a confirmation).
 */
export function ReviewsPage() {
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const status = statusOf(params);
  const reviews = useInfiniteQuery({
    queryKey: ['admin', 'reviews', 'list', status],
    queryFn: ({ pageParam, signal }) => fetchReviews(status, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => (last.hasMore ? page + 1 : undefined),
  });
  const items = reviews.data?.pages.flatMap((page) => page.items) ?? [];

  /** Runs a change and reloads the tabs; {@code change} answers whether the review was still there. */
  async function run(review: ModeratedReview, change: () => Promise<boolean>, done: string) {
    setNotice(null);
    setBusy(review.id);
    try {
      setNotice((await change()) ? { kind: 'ok', text: done } : { kind: 'error', text: messages.notFound });
    } catch {
      setNotice({ kind: 'error', text: es.admin.actionError });
    } finally {
      setBusy(null);
      await queryClient.invalidateQueries({ queryKey: ['admin', 'reviews'] });
    }
  }

  function decide(review: ModeratedReview, decision: 'approve' | 'reject') {
    void run(
      review,
      async () => (await moderateReview(review.id, decision)).status === 'done',
      decision === 'approve' ? messages.approved(review.authorName) : messages.rejected(review.authorName),
    );
  }

  function remove(review: ModeratedReview) {
    if (window.confirm(messages.confirmDelete(review.authorName))) {
      void run(
        review,
        async () => (await deleteReview(review.id)).status === 'deleted',
        messages.deleted(review.authorName),
      );
    }
  }

  return (
    <>
      <title>{`${messages.title} | ${es.admin.title}`}</title>
      <div className="admin-title-row">
        <h1>{messages.title}</h1>
      </div>
      <div className="admin-tabs" role="group" aria-label={messages.tabs}>
        {REVIEW_STATUSES.map((value) => (
          <button
            key={value}
            type="button"
            className="btn btn-secondary"
            aria-pressed={value === status}
            onClick={() => {
              setNotice(null);
              setParams(value === 'PENDING' ? {} : { [STATUS_PARAM]: value }, {
                replace: true,
              });
            }}
          >
            {messages.statuses[value]}
          </button>
        ))}
      </div>
      <Feedback notice={notice} />
      {reviews.isPending && <p role="status">{es.admin.loading}</p>}
      {reviews.isError && items.length === 0 && (
        <div role="alert">
          <p>{es.admin.loadError}</p>
          <button type="button" className="btn" onClick={() => void reviews.refetch()}>
            {es.admin.retry}
          </button>
        </div>
      )}
      {reviews.isSuccess && items.length === 0 && <p>{messages.empty[status]}</p>}
      {items.length > 0 && (
        <ul className="admin-list">
          {items.map((review) => (
            <li key={review.id} className="admin-card">
              <div className="admin-card-head">
                <span className="admin-card-title">
                  {review.product.slug !== null && review.product.name !== null ? (
                    <Link to={`/producto/${review.product.slug}`}>{review.product.name}</Link>
                  ) : (
                    messages.productGone
                  )}
                </span>
                <span className="admin-badge">{messages.statuses[review.status]}</span>
              </div>
              <p className="admin-card-meta">
                <span className="admin-review-stars" role="img" aria-label={messages.rating(review.rating)}>
                  <span aria-hidden="true">{'★'.repeat(review.rating) + '☆'.repeat(MAX_STARS - review.rating)}</span>
                </span>{' '}
                <span>{messages.submitted(review.authorName, formatDateTime(review.updatedAt))}</span>
              </p>
              <p className="admin-review-text">{review.text ?? messages.noText}</p>
              <div className="admin-actions">
                {review.status !== 'APPROVED' && (
                  <button
                    type="button"
                    className="btn btn-secondary"
                    disabled={busy !== null}
                    onClick={() => {
                      decide(review, 'approve');
                    }}
                  >
                    {messages.approve}
                  </button>
                )}
                {review.status !== 'REJECTED' && (
                  <button
                    type="button"
                    className="btn btn-secondary"
                    disabled={busy !== null}
                    onClick={() => {
                      decide(review, 'reject');
                    }}
                  >
                    {messages.reject}
                  </button>
                )}
                <button
                  type="button"
                  className="btn btn-secondary danger"
                  disabled={busy !== null}
                  onClick={() => {
                    remove(review);
                  }}
                >
                  {messages.delete}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {reviews.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary show-more-btn"
          disabled={reviews.isFetchingNextPage}
          onClick={() => void reviews.fetchNextPage()}
        >
          {reviews.isFetchingNextPage ? es.admin.loading : es.admin.showMore}
        </button>
      )}
    </>
  );
}
