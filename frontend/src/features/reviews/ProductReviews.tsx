import { useId } from 'react';
import { es } from '../../i18n/es.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import { useProductReviews } from './useProductReviews.ts';
import './reviews.css';

const messages = es.reviews.product;

/** Where the summary near the price takes the visitor. */
const REVIEWS_ANCHOR = 'opiniones';

const MAX_STARS = 5;

/** The average with one decimal, as the shop writes it: "4,5". */
const ratingFormat = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Filled stars up to the rating rounded, empty ones after; the label says the exact rating. */
function Stars({ rating, label }: { rating: number; label: string }) {
  const filled = Math.round(rating);
  return (
    <span className="review-stars" role="img" aria-label={label}>
      <span aria-hidden="true">{'★'.repeat(filled) + '☆'.repeat(MAX_STARS - filled)}</span>
    </span>
  );
}

/** UC-24 (T-E5-07): the average and count near the price; nothing until the product has approved reviews. */
export function RatingSummary({ slug }: { slug: string }) {
  const reviews = useProductReviews(slug);
  const first = reviews.data?.pages[0];
  if (first === undefined || first === null || first.average === null) {
    return null;
  }
  const average = ratingFormat.format(first.average);
  return (
    <a className="pdp-rating" href={`#${REVIEWS_ANCHOR}`}>
      <Stars rating={first.average} label={messages.stars(average)} />
      <span>{messages.summary(average, first.count)}</span>
    </a>
  );
}

/**
 * UC-24 (T-E5-07): the approved reviews below the product, newest first, with their average and count and "Ver más"
 * for older ones. Only the author's first name shows.
 */
export function ProductReviewsSection({ slug }: { slug: string }) {
  const reviews = useProductReviews(slug);
  const headingId = useId();
  const first = reviews.data?.pages[0];

  if (first === null) {
    return null;
  }

  const list = reviews.data?.pages.flatMap((page) => page?.reviews ?? []);

  return (
    <section id={REVIEWS_ANCHOR} className="product-reviews" aria-labelledby={headingId}>
      <h2 id={headingId}>{messages.title}</h2>
      {reviews.isPending && <p role="status">{messages.loading}</p>}
      {reviews.isError && list === undefined && (
        <div className="product-reviews-error" role="alert">
          <p>{messages.loadError}</p>
          <button type="button" className="btn btn-secondary" onClick={() => void reviews.refetch()}>
            {messages.retry}
          </button>
        </div>
      )}
      {first !== undefined && first.average !== null && (
        <p className="product-reviews-summary">
          <Stars rating={first.average} label={messages.stars(ratingFormat.format(first.average))} />
          <span>{messages.summary(ratingFormat.format(first.average), first.count)}</span>
        </p>
      )}
      {list?.length === 0 && <p className="product-reviews-empty">{messages.empty}</p>}
      {list !== undefined && list.length > 0 && (
        <ul className="product-review-list">
          {list.map((review, index) => (
            <li key={`${review.createdAt}-${String(index)}`} className="product-review">
              <Stars rating={review.rating} label={messages.rating(review.rating)} />
              <p className="product-review-byline">
                <strong>{review.authorName}</strong> · {formatDate(new Date(review.createdAt))}
              </p>
              {review.text !== null && <p className="product-review-text">{review.text}</p>}
            </li>
          ))}
        </ul>
      )}
      {reviews.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary"
          disabled={reviews.isFetchingNextPage}
          onClick={() => void reviews.fetchNextPage()}
        >
          {reviews.isFetchingNextPage ? messages.loadingMore : messages.more}
        </button>
      )}
      {reviews.isFetchNextPageError && (
        <p className="product-reviews-error" role="alert">
          {messages.moreError}
        </p>
      )}
    </section>
  );
}
