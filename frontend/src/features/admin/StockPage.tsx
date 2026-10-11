import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type SubmitEvent } from 'react';
import { Link, useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatDateTime } from '../../i18n/formatDate.ts';
import { parseQuantity } from './adminInput.ts';
import { newIdempotencyKey } from './adminSession.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import { adjustStock, fetchMovements, type AdjustAnswer, type StockMovement } from './stockApi.ts';
import { useAdminProduct } from './useAdminProduct.ts';

const messages = es.admin.stock;

function stockKey(variantId: string) {
  return ['admin', 'stock', variantId] as const;
}

function adjustNotice(answer: AdjustAnswer): Notice {
  switch (answer.status) {
    case 'adjusted':
      return { kind: 'ok', text: messages.adjusted(answer.onHand) };
    case 'outOfRange':
      return {
        kind: 'error',
        text:
          answer.problem === 'WOULD_GO_NEGATIVE'
            ? messages.wouldGoNegative(answer.onHand)
            : messages.wouldExceedMaximum(answer.onHand),
      };
    case 'invalid':
      return { kind: 'error', text: messages.problems[answer.problem] };
    case 'unknownVariant':
      return { kind: 'error', text: messages.variantNotFound };
  }
}

/**
 * One adjustment (UC-07). The key belongs to one quantity and note: a retry after a lost answer sends it again so the
 * stock changes once, and any edit gets a new one.
 */
function AdjustForm({ variantId, onAdjusted }: { variantId: string; onAdjusted: () => Promise<void> }) {
  const titleId = useId();
  const quantityId = useId();
  const [quantity, setQuantity] = useState('');
  const [note, setNote] = useState('');
  const [key, setKey] = useState(newIdempotencyKey);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);

  function edit(next: { quantity?: string; note?: string }) {
    if (next.quantity !== undefined) {
      setQuantity(next.quantity);
    }
    if (next.note !== undefined) {
      setNote(next.note);
    }
    setKey(newIdempotencyKey());
    setNotice(null);
  }

  function step(delta: number) {
    edit({ quantity: String((parseQuantity(quantity) ?? 0) + delta) });
  }

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const units = parseQuantity(quantity);
    if (units === null) {
      setNotice({ kind: 'error', text: messages.quantityInvalid });
      return;
    }
    setSaving(true);
    setNotice(null);
    try {
      const answer = await adjustStock(variantId, units, note.trim(), key);
      setNotice(adjustNotice(answer));
      if (answer.status === 'adjusted') {
        setQuantity('');
        setNote('');
        setKey(newIdempotencyKey());
        await onAdjusted();
      } else if (answer.status === 'invalid' && answer.problem === 'KEY_REUSED') {
        setKey(newIdempotencyKey());
      }
    } catch {
      // The same key goes again with the next tap.
      setNotice({ kind: 'error', text: messages.connectionLost });
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="admin-section" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.adjust}</h2>
      <form className="admin-form" onSubmit={(event) => void submit(event)}>
        <div className="field">
          <label htmlFor={quantityId}>{messages.quantity}</label>
          <div className="stepper">
            <button
              type="button"
              className="btn btn-secondary icon-text"
              aria-label={messages.decrease}
              onClick={() => {
                step(-1);
              }}
            >
              −
            </button>
            <input
              id={quantityId}
              type="number"
              inputMode="numeric"
              step={1}
              required
              value={quantity}
              onChange={(event) => {
                edit({ quantity: event.target.value });
              }}
            />
            <button
              type="button"
              className="btn btn-secondary icon-text"
              aria-label={messages.increase}
              onClick={() => {
                step(1);
              }}
            >
              +
            </button>
          </div>
        </div>
        <label className="field">
          <span>{messages.note}</span>
          <input
            value={note}
            maxLength={200}
            onChange={(event) => {
              edit({ note: event.target.value });
            }}
          />
        </label>
        <Feedback notice={notice} />
        <button type="submit" className="btn" disabled={saving}>
          {saving ? messages.submitting : messages.submit}
        </button>
      </form>
    </section>
  );
}

function MovementItem({ movement }: { movement: StockMovement }) {
  return (
    <li className="admin-card movement">
      <p className="movement-quantity">{messages.signedQuantity(movement.quantity)}</p>
      <div>
        <p>{movement.note ?? messages.types[movement.type]}</p>
        <p className="admin-card-meta">
          <time dateTime={movement.createdAt.toISOString()}>{formatDateTime(movement.createdAt)}</time> ·{' '}
          {movement.userName ?? messages.unknownUser}
        </p>
      </div>
    </li>
  );
}

/** UC-07 at /admin/productos/:productId/variantes/:variantId/stock: stock, adjustment and history of one variant. */
export function StockPage() {
  const { productId = '', variantId = '' } = useParams();
  const product = useAdminProduct(productId);
  const queryClient = useQueryClient();
  const historyId = useId();
  const history = useInfiniteQuery({
    queryKey: stockKey(variantId),
    queryFn: ({ pageParam, signal }) => fetchMovements(variantId, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => (last.status === 'listed' && last.hasMore ? page + 1 : undefined),
    staleTime: 0,
  });

  const variant =
    product.data?.status === 'found'
      ? product.data.product.variants.find((candidate) => candidate.id === variantId)
      : undefined;
  const pages = history.data?.pages ?? [];
  const first = pages[0];
  const movements = pages.flatMap((page) => (page.status === 'listed' ? page.items : []));

  return (
    <>
      <Link to={`/admin/productos/${productId}`} className="back-link">
        {messages.backToProduct}
      </Link>
      <title>{`${messages.title} | ${es.admin.title}`}</title>
      <h1>{messages.title}</h1>
      {product.data?.status === 'found' && variant !== undefined && (
        <p className="admin-lead">
          {messages.variant(product.data.product.name, variant.size, variant.color)} ·{' '}
          {es.admin.variants.sku} {variant.sku}
        </p>
      )}
      {history.isPending && <p role="status">{es.admin.loading}</p>}
      {history.isError && first === undefined && (
        <div role="alert">
          <p>{es.admin.loadError}</p>
          <button type="button" className="btn" onClick={() => void history.refetch()}>
            {es.admin.retry}
          </button>
        </div>
      )}
      {first?.status === 'unknownVariant' && <p role="alert">{messages.variantNotFound}</p>}
      {first?.status === 'listed' && (
        <>
          <p className="stock-on-hand">{messages.onHand(first.onHand)}</p>
          <AdjustForm
            variantId={variantId}
            onAdjusted={() => queryClient.invalidateQueries({ queryKey: stockKey(variantId) })}
          />
          <section className="admin-section" aria-labelledby={historyId}>
            <h2 id={historyId}>{messages.history}</h2>
            {movements.length === 0 ? (
              <p>{messages.noMovements}</p>
            ) : (
              <ol className="admin-list">
                {movements.map((movement) => (
                  <MovementItem key={movement.id} movement={movement} />
                ))}
              </ol>
            )}
            {history.hasNextPage && (
              <button
                type="button"
                className="btn btn-secondary show-more-btn"
                disabled={history.isFetchingNextPage}
                onClick={() => void history.fetchNextPage()}
              >
                {history.isFetchingNextPage ? es.admin.loading : es.admin.showMore}
              </button>
            )}
          </section>
        </>
      )}
    </>
  );
}
