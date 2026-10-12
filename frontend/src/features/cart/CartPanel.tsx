import { useEffect, useId, useRef, type MouseEvent } from 'react';
import { Link } from 'react-router';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { Cart, CartItem } from './cartApi.ts';
import { useCartPanel } from './CartPanelContext.tsx';
import { useAcknowledgePrices, useCart, useChangeItem } from './useCart.ts';
import './cart.css';

/** Why an item cannot be bought as it is (UC-09); an AVAILABLE item has nothing to say. */
function statusNote(item: CartItem): string | null {
  switch (item.status) {
    case 'UNAVAILABLE':
      return es.cart.soldOut;
    case 'INSUFFICIENT_STOCK':
      return `${es.product.onlyLeft(item.available)}. ${es.cart.lowerToBuy}`;
    case 'AVAILABLE':
      return null;
  }
}

/** The answer to the last change of an item: how many are left after a 409, or a request that failed. */
function ChangeProblem({ item, change }: { item: CartItem; change: ReturnType<typeof useChangeItem> }) {
  if (change.isError) {
    return es.cart.changeError;
  }
  // When the item cannot be bought any more its status says why.
  if (change.data?.status === 'insufficientStock' && item.status === 'AVAILABLE') {
    return es.product.onlyLeft(change.data.available);
  }
  return null;
}

/** The quantity of an item with "−" (at 1 it removes the item), "+" up to the available stock, and "Quitar". */
function QuantityControl({ item, name }: { item: CartItem; name: string }) {
  const change = useChangeItem(item.id);
  function setQuantity(quantity: number) {
    change.mutate({ quantity });
  }
  return (
    <>
      <div className="cart-item-actions">
        <div className="cart-qty" role="group" aria-label={es.cart.quantityOf(name)}>
          <button
            type="button"
            className="qty-btn"
            aria-label={es.cart.decrease(name)}
            disabled={change.isPending}
            onClick={() => {
              setQuantity(item.quantity - 1);
            }}
          >
            −
          </button>
          <span className="cart-qty-value" aria-live="polite">
            {item.quantity}
          </span>
          <button
            type="button"
            className="qty-btn"
            aria-label={es.cart.increase(name)}
            disabled={change.isPending || item.quantity >= item.available}
            onClick={() => {
              setQuantity(item.quantity + 1);
            }}
          >
            +
          </button>
        </div>
        <button
          type="button"
          className="link-btn cart-remove"
          aria-label={es.cart.removeLabel(name)}
          disabled={change.isPending}
          onClick={() => {
            change.mutate(null);
          }}
        >
          {es.cart.remove}
        </button>
      </div>
      <p className="cart-item-problem" role="alert">
        <ChangeProblem item={item} change={change} />
      </p>
    </>
  );
}

function Item({ item, onFollowLink }: { item: CartItem; onFollowLink: () => void }) {
  const { product } = item;
  const note = statusNote(item);
  return (
    <li className={item.status === 'UNAVAILABLE' ? 'cart-item unavailable' : 'cart-item'}>
      {product?.image != null ? (
        <BlurredImage
          src={product.image.src}
          srcSet={product.image.srcSet}
          sizes="66px"
          alt={es.cart.photoAlt(product.name, product.color)}
          {...(product.image.placeholder === undefined ? {} : { placeholder: product.image.placeholder })}
        />
      ) : (
        <div className="ph" aria-hidden="true" />
      )}
      <div className="cart-item-info">
        {product === null ? (
          <p className="cart-item-name">{es.cart.unknownProduct}</p>
        ) : (
          <>
            <Link to={`/producto/${product.slug}`} className="cart-item-name" onClick={onFollowLink}>
              {product.name}
            </Link>
            <p className="cart-item-meta">{es.cart.variant(product.color, product.size)}</p>
          </>
        )}
        {item.previousUnitPriceCents !== null && (
          <p className="cart-item-meta cart-price-change">
            <span className="visually-hidden">{es.cart.previousPrice}: </span>
            <del>{formatMoney(item.previousUnitPriceCents)}</del>{' '}
            <span className="visually-hidden">{es.cart.currentPrice}: </span>
            {formatMoney(item.unitPriceCents)}
          </p>
        )}
        {note !== null && <p className="cart-item-note">{note}</p>}
        <QuantityControl item={item} name={product?.name ?? es.cart.unknownProduct} />
      </div>
      {/* Only AVAILABLE items count in the subtotal, so only they show a line amount. */}
      {item.status === 'AVAILABLE' && (
        <p className="cart-item-price">{formatMoney(item.unitPriceCents * item.quantity)}</p>
      )}
    </li>
  );
}

/** UC-09: some prices changed since the customer last saw them; "Entendido" acknowledges them. */
function PriceChangeNotice() {
  const acknowledge = useAcknowledgePrices();
  return (
    <div className="cart-notice">
      <p>{es.cart.pricesChanged}</p>
      <button
        type="button"
        className="link-btn"
        disabled={acknowledge.isPending}
        onClick={() => {
          acknowledge.mutate();
        }}
      >
        {es.cart.acknowledge}
      </button>
      <p className="cart-item-problem" role="alert">
        {acknowledge.isError ? es.cart.acknowledgeError : null}
      </p>
    </div>
  );
}

function Totals({ cart }: { cart: Cart }) {
  return (
    <dl className="cart-totals">
      <div className="cart-row">
        <dt>{es.cart.subtotal}</dt>
        <dd>{formatMoney(cart.subtotalCents)}</dd>
      </div>
      {cart.discountCents > 0 && (
        <div className="cart-row">
          <dt>{es.cart.discount}</dt>
          <dd>−{formatMoney(cart.discountCents)}</dd>
        </div>
      )}
      <div className="cart-row total">
        <dt>{es.cart.total}</dt>
        <dd>{formatMoney(cart.totalCents)}</dd>
      </div>
    </dl>
  );
}

/**
 * The side panel of the cart (UC-08): a modal dialog from the right edge over a dimmed page, with the items and the
 * totals as the server sent them. The dialog keeps the focus inside and closes with Escape.
 */
export function CartPanel() {
  const panel = useCartPanel();
  const cart = useCart();
  const dialog = useRef<HTMLDialogElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const titleId = useId();

  useEffect(() => {
    const element = dialog.current;
    if (element === null) {
      return;
    }
    if (panel.isOpen && !element.open) {
      element.showModal();
    } else if (!panel.isOpen && element.open) {
      element.close();
    }
  }, [panel.isOpen]);

  // A removed item takes its focused button with it: the focus goes to the close button, not out of the dialog.
  const itemCount = cart.data?.items.length;
  useEffect(() => {
    const element = dialog.current;
    if (element?.open === true && !element.contains(document.activeElement)) {
      closeButton.current?.focus();
    }
  }, [itemCount]);

  function closeOnBackdrop(event: MouseEvent<HTMLDialogElement>) {
    if (event.target === event.currentTarget) {
      panel.close();
    }
  }

  return (
    <dialog
      ref={dialog}
      className="cart-panel"
      aria-labelledby={titleId}
      onClose={panel.closed}
      onClick={closeOnBackdrop}
    >
      <div className="cart-panel-inner">
        <header className="cart-head">
          <h2 id={titleId}>{es.cart.title}</h2>
          <button
            ref={closeButton}
            type="button"
            className="icon-btn"
            aria-label={es.cart.close}
            onClick={panel.close}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </header>
        {cart.isPending && <p className="cart-body" role="status">{es.cart.loading}</p>}
        {cart.isError && (
          <div className="cart-body" role="alert">
            <p>{es.cart.loadError}</p>
            <button type="button" className="link-btn" onClick={() => void cart.refetch()}>
              {es.cart.retry}
            </button>
          </div>
        )}
        {cart.data !== undefined && cart.data.items.length === 0 && (
          <div className="cart-body cart-empty">
            <p>{es.cart.empty}</p>
            <p>{es.cart.emptyHint}</p>
            <button type="button" className="btn" onClick={panel.close}>
              {es.cart.keepShopping}
            </button>
          </div>
        )}
        {cart.data !== undefined && cart.data.items.length > 0 && (
          <>
            <ul className="cart-body cart-items">
              {cart.data.items.map((item) => (
                <Item key={item.id} item={item} onFollowLink={panel.close} />
              ))}
            </ul>
            <footer className="cart-foot">
              {cart.data.items.some((item) => item.previousUnitPriceCents !== null) && <PriceChangeNotice />}
              <Totals cart={cart.data} />
            </footer>
          </>
        )}
      </div>
    </dialog>
  );
}
