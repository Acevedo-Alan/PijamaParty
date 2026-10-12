import { useEffect, useId, useRef, type MouseEvent } from 'react';
import { Link } from 'react-router';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { Cart, CartItem } from './cartApi.ts';
import { useCartPanel } from './CartPanelContext.tsx';
import { useCart } from './useCart.ts';
import './cart.css';

function Item({ item, onFollowLink }: { item: CartItem; onFollowLink: () => void }) {
  const { product } = item;
  return (
    <li className="cart-item">
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
        <p className="cart-item-meta">{es.cart.quantity(item.quantity)}</p>
      </div>
      <p className="cart-item-price">{formatMoney(item.unitPriceCents * item.quantity)}</p>
    </li>
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
          <button type="button" className="icon-btn" aria-label={es.cart.close} onClick={panel.close}>
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
              <Totals cart={cart.data} />
            </footer>
          </>
        )}
      </div>
    </dialog>
  );
}
