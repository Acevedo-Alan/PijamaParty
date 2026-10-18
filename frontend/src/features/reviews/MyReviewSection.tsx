import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type SyntheticEvent } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { SIGN_IN_PATH } from '../account/AccountLink.tsx';
import { CUSTOMER_KEY, useCustomer } from '../account/useCustomer.ts';
import { fetchMyReview, myReviewKey, submitReview, type MyReview, type ReviewField } from './myReviewApi.ts';
import './reviews.css';

const messages = es.reviews.mine;

/** The most characters of a review's text (UC-24); the server refuses more. */
const MAX_TEXT = 1000;

const RATINGS = [1, 2, 3, 4, 5] as const;

function StatusNote({ review }: { review: MyReview }) {
  return (
    <p className="my-review-status">
      {messages.statusLabel} <strong>{messages.status[review.status]}</strong>
    </p>
  );
}

interface ReviewFormProps {
  productId: string;
  review: MyReview | null;
}

/** The rating from 1 to 5 and the optional text; editing a review sends it back to moderation. */
function ReviewForm({ productId, review }: ReviewFormProps) {
  const queryClient = useQueryClient();
  const [rating, setRating] = useState<number | null>(review?.rating ?? null);
  const [text, setText] = useState(review?.text ?? '');
  const [invalid, setInvalid] = useState<ReviewField[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const textId = useId();
  const ratingErrorId = useId();
  const textErrorId = useId();

  const submit = useMutation({
    mutationFn: (chosen: number) => submitReview(productId, chosen, text),
    onSuccess: (answer) => {
      switch (answer.status) {
        case 'saved':
          setInvalid([]);
          setNotice(messages.sent);
          queryClient.setQueryData(myReviewKey(productId), { eligible: true, review: answer.review });
          return;
        case 'invalid':
          setNotice(null);
          setInvalid(answer.fields);
          return;
        case 'notABuyer':
          setNotice(messages.notABuyer);
          void queryClient.invalidateQueries({ queryKey: myReviewKey(productId) });
          return;
        case 'notFound':
          setNotice(messages.error);
          return;
        case 'signedOut':
          queryClient.setQueryData(CUSTOMER_KEY, null);
          return;
      }
    },
    onError: () => {
      setNotice(messages.error);
    },
  });

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    if (rating === null) {
      setNotice(null);
      setInvalid(['rating']);
      return;
    }
    submit.mutate(rating);
  }

  const ratingInvalid = invalid.includes('rating');
  const textInvalid = invalid.includes('text');

  return (
    <form className="my-review-form" onSubmit={send} noValidate>
      <fieldset
        className="option-group"
        aria-invalid={ratingInvalid}
        {...(ratingInvalid ? { 'aria-describedby': ratingErrorId } : {})}
      >
        <legend>{messages.rating}</legend>
        <div className="chips">
          {RATINGS.map((value) => (
            <label key={value} className="chip">
              <input
                type="radio"
                name="rating"
                value={value}
                checked={rating === value}
                aria-label={messages.stars(value)}
                onChange={() => {
                  setRating(value);
                  setInvalid(invalid.filter((field) => field !== 'rating'));
                }}
              />
              <span aria-hidden="true">{`${String(value)} ★`}</span>
            </label>
          ))}
        </div>
        {ratingInvalid && (
          <p id={ratingErrorId} className="my-review-error">
            {messages.pickRating}
          </p>
        )}
      </fieldset>
      <label htmlFor={textId} className="my-review-label">
        {messages.text}
      </label>
      <textarea
        id={textId}
        value={text}
        rows={4}
        maxLength={MAX_TEXT}
        aria-invalid={textInvalid}
        {...(textInvalid ? { 'aria-describedby': textErrorId } : {})}
        onChange={(event) => {
          setText(event.target.value);
        }}
      />
      <p className="my-review-count">{messages.count(text.length, MAX_TEXT)}</p>
      {textInvalid && (
        <p id={textErrorId} className="my-review-error">
          {messages.textTooLong(MAX_TEXT)}
        </p>
      )}
      <button type="submit" className="btn" disabled={submit.isPending}>
        {submit.isPending ? messages.sending : review === null ? messages.send : messages.save}
      </button>
      <p className="my-review-notice" role="status">
        {notice}
      </p>
    </form>
  );
}

/**
 * UC-24 (T-E5-06): "Tu opinión" on the product page. A visitor is invited to sign in; a customer who bought the
 * product rates it and edits its review, which waits for moderation; one who did not is told only buyers can.
 */
export function MyReviewSection({ productId }: { productId: string }) {
  const customer = useCustomer();
  const headingId = useId();
  const standing = useQuery({
    queryKey: myReviewKey(productId),
    queryFn: ({ signal }) => fetchMyReview(productId, signal),
    enabled: customer !== undefined && customer !== null,
  });

  if (customer === undefined) {
    return null;
  }

  let body;
  if (customer === null || standing.data === null) {
    body = (
      <p className="my-review-intro">
        <Link to={SIGN_IN_PATH}>{messages.signIn}</Link> {messages.signInToReview}
      </p>
    );
  } else if (standing.isError) {
    body = (
      <p className="my-review-intro" role="alert">
        {messages.loadError}
      </p>
    );
  } else if (standing.data === undefined) {
    body = <p className="my-review-intro">{messages.loading}</p>;
  } else if (standing.data.eligible) {
    body = (
      <>
        {standing.data.review !== null && <StatusNote review={standing.data.review} />}
        <ReviewForm productId={productId} review={standing.data.review} />
      </>
    );
  } else if (standing.data.review !== null) {
    // A review stays when the order is cancelled later, but it can no longer be edited.
    body = (
      <>
        <StatusNote review={standing.data.review} />
        <p className="my-review-intro">{messages.yourRating(standing.data.review.rating)}</p>
        {standing.data.review.text !== null && <p className="my-review-text">{standing.data.review.text}</p>}
      </>
    );
  } else {
    body = <p className="my-review-intro">{messages.notABuyer}</p>;
  }

  return (
    <section className="my-review" aria-labelledby={headingId}>
      <h2 id={headingId}>{messages.title}</h2>
      {body}
    </section>
  );
}
