import { useId, useState, type SubmitEvent } from 'react';
import { Link } from 'react-router';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { centsToPesosInput, parsePesos, splitList } from './adminInput.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import { generateVariants, updateVariant, type AdminProduct, type AdminVariant, type VariantAnswer } from './productsApi.ts';
import { useStoreProduct } from './useAdminProduct.ts';

const messages = esAdmin.variants;

/** The variants by color and then size, in the display order of the product (UC-06). */
export function inDisplayOrder(product: AdminProduct): AdminVariant[] {
  const rank = (values: string[], value: string) => values.indexOf(value);
  return product.variants.toSorted(
    (a, b) =>
      rank(product.colors, a.color) - rank(product.colors, b.color) ||
      rank(product.sizes, a.size) - rank(product.sizes, b.size),
  );
}

function variantNotice(answer: VariantAnswer): Notice {
  switch (answer.status) {
    case 'saved':
      return { kind: 'ok', text: esAdmin.saved };
    case 'lastActivePricedVariant':
      return { kind: 'error', text: messages.lastActivePriced };
    case 'invalidPrice':
      return { kind: 'error', text: messages.priceOutOfRange };
    case 'notFound':
      return { kind: 'error', text: messages.notFound };
  }
}

function VariantRow({ productId, variant }: { productId: string; variant: AdminVariant }) {
  const store = useStoreProduct();
  const [price, setPrice] = useState(() => (variant.priceCents === null ? '' : centsToPesosInput(variant.priceCents)));
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);

  async function save(change: { priceCents: number } | { active: boolean }) {
    setSaving(true);
    setNotice(null);
    try {
      const answer = await updateVariant(variant.id, change);
      if (answer.status === 'saved') {
        store(answer.product);
      }
      setNotice(variantNotice(answer));
    } catch {
      setNotice({ kind: 'error', text: esAdmin.actionError });
    } finally {
      setSaving(false);
    }
  }

  function submitPrice(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const cents = parsePesos(price);
    if (cents === null) {
      setNotice({ kind: 'error', text: messages.priceInvalid });
      return;
    }
    void save({ priceCents: cents });
  }

  return (
    <li className="admin-card variant-row">
      <div className="admin-card-head">
        <p className="admin-card-title">
          {variant.size} · {variant.color}
        </p>
        <p className="admin-card-meta">
          {messages.sku} {variant.sku}
          {variant.priceCents === null && ` · ${messages.noPrice}`}
        </p>
      </div>
      <form className="inline-form" onSubmit={submitPrice}>
        <label className="field">
          <span>{messages.priceLabel(variant.size, variant.color)}</span>
          <input
            inputMode="decimal"
            value={price}
            onChange={(event) => {
              setPrice(event.target.value);
            }}
          />
        </label>
        <button type="submit" className="btn btn-secondary" disabled={saving}>
          {messages.savePrice}
        </button>
      </form>
      <div className="admin-actions">
        <label className="switch">
          <input
            type="checkbox"
            checked={variant.active}
            disabled={saving}
            aria-label={messages.activeLabel(variant.size, variant.color)}
            onChange={(event) => void save({ active: event.target.checked })}
          />
          <span aria-hidden="true">{messages.active}</span>
        </label>
        <Link
          to={`/admin/productos/${productId}/variantes/${variant.id}/stock`}
          aria-label={messages.stockLink(variant.size, variant.color)}
        >
          {messages.stock}
        </Link>
      </div>
      <Feedback notice={notice} />
    </li>
  );
}

/** UC-06 steps 2 and 3: generate the size × color matrix, then price and activate each variant. */
export function VariantsSection({ product }: { product: AdminProduct }) {
  const store = useStoreProduct();
  const titleId = useId();
  const hintId = useId();
  const [sizes, setSizes] = useState('');
  const [colors, setColors] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);

  async function generate(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setNotice(null);
    try {
      const answer = await generateVariants(product, splitList(sizes), splitList(colors));
      switch (answer.status) {
        case 'generated':
          store(answer.product);
          setSizes('');
          setColors('');
          setNotice({ kind: 'ok', text: messages.created(answer.created) });
          break;
        case 'invalid':
          setNotice({ kind: 'error', text: messages.problems[answer.problem] });
          break;
        case 'notFound':
          setNotice({ kind: 'error', text: esAdmin.productEdit.notFound });
          break;
      }
    } catch {
      setNotice({ kind: 'error', text: esAdmin.actionError });
    } finally {
      setSaving(false);
    }
  }

  const variants = inDisplayOrder(product);

  return (
    <section className="admin-section" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.title}</h2>
      <form className="admin-form" onSubmit={(event) => void generate(event)}>
        <p id={hintId} className="field-hint">
          {messages.listHint}
        </p>
        <label className="field">
          <span>{messages.sizes}</span>
          <input
            value={sizes}
            required
            aria-describedby={hintId}
            onChange={(event) => {
              setSizes(event.target.value);
            }}
          />
        </label>
        <label className="field">
          <span>{messages.colors}</span>
          <input
            value={colors}
            required
            aria-describedby={hintId}
            onChange={(event) => {
              setColors(event.target.value);
            }}
          />
        </label>
        <Feedback notice={notice} />
        <button type="submit" className="btn" disabled={saving}>
          {messages.generate}
        </button>
      </form>
      {variants.length === 0 ? (
        <p>{messages.empty}</p>
      ) : (
        <ul className="admin-list">
          {variants.map((variant) => (
            <VariantRow key={variant.id} productId={product.id} variant={variant} />
          ))}
        </ul>
      )}
    </section>
  );
}
