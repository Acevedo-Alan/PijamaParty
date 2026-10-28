import { useEffect, useId, useRef, useState, type SubmitEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { Toast } from '../../components/motion/Toast.tsx';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { isCouponOutdone, type Cart, type CartItem, type CouponRefusal } from './cartApi.ts';
import { useCartPanel } from './CartPanelContext.tsx';
import { FreeShippingProgress } from './FreeShippingProgress.tsx';
import {
  useAcknowledgePrices,
  useApplyCoupon,
  useCart,
  useChangeItem,
  useRemoveCoupon,
} from './useCart.ts';
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

/** Why a coupon does not discount (UC-10): a refused code, or the status of the applied one. */
function couponReason(reason: CouponRefusal, missingCents: number): string {
  return reason === 'BELOW_MINIMUM'
    ? es.cart.couponBelowMinimum(formatMoney(missingCents))
    : es.cart.couponRefused[reason];
}

/** The answer to the last code: why it was refused, too many attempts, or a request that failed. */
function applyProblem(apply: ReturnType<typeof useApplyCoupon>): string | null {
  if (apply.isError) {
    return es.cart.couponApplyError;
  }
  switch (apply.data?.status) {
    case 'refused':
      return couponReason(apply.data.reason, apply.data.missingCents);
    case 'tooManyAttempts':
      return es.cart.couponTooManyAttempts;
    default:
      return null;
  }
}

/**
 * UC-10: the coupon field, and the applied coupon with "Quitar" and why it does not discount when it does not: it no
 * longer applies, or an automatic coupon takes more (UC-23).
 */
function CouponField({ cart }: { cart: Cart }) {
  const { coupon } = cart;
  const apply = useApplyCoupon();
  const remove = useRemoveCoupon();
  const [code, setCode] = useState('');
  const inputId = useId();
  const problemId = useId();
  const busy = apply.isPending || remove.isPending;
  const problem = applyProblem(apply);

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (code.trim() === '' || busy) {
      return;
    }
    apply.mutate(code, {
      onSuccess: (answer) => {
        if (answer.status === 'applied') {
          setCode('');
        }
      },
    });
  }

  return (
    <div className="cart-coupon">
      {coupon !== null && (
        <>
          <div className="cart-coupon-applied">
            <p>{es.cart.coupon(coupon.code)}</p>
            <button
              type="button"
              className="link-btn"
              aria-label={es.cart.removeCouponLabel(coupon.code)}
              disabled={busy}
              onClick={() => {
                remove.mutate();
              }}
            >
              {es.cart.removeCoupon}
            </button>
          </div>
          {coupon.status !== 'APPLICABLE' && (
            <p className="cart-item-note">{couponReason(coupon.status, coupon.missingCents)}</p>
          )}
          {isCouponOutdone(cart) && <p className="cart-item-note">{es.cart.couponNotAdded(coupon.code)}</p>}
          <p className="cart-item-problem" role="alert">
            {remove.isError ? es.cart.couponRemoveError : null}
          </p>
        </>
      )}
      <form className="cart-coupon-form" onSubmit={submit}>
        <label htmlFor={inputId} className="visually-hidden">
          {es.cart.couponCode}
        </label>
        <input
          id={inputId}
          value={code}
          placeholder={es.cart.couponCode}
          maxLength={20}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          // Read-only rather than disabled, so the field keeps the focus while the code is sent with Enter.
          readOnly={busy}
          aria-invalid={problem !== null}
          aria-describedby={problemId}
          onChange={(event) => {
            setCode(event.target.value);
            apply.reset();
          }}
        />
        <button type="submit" className="btn btn-secondary" disabled={busy || code.trim() === ''}>
          {es.cart.applyCoupon}
        </button>
      </form>
      <p id={problemId} className="cart-item-problem" role="alert">
        {problem}
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
          <dt>
            {cart.appliedDiscount === null ? es.cart.discount : es.cart.discountWith(cart.appliedDiscount.code)}
          </dt>
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

/** How long the "Agregado al carrito" notice stays (E7). */
const NOTICE_MS = 4000;

/**
 * UC-08 (E7): whether "Agregado al carrito" shows. Each add from the product page shows it for 4 s again; closing it or
 * the panel hides it.
 */
function useAddedNotice(added: number, panelOpen: boolean) {
  const [shown, setShown] = useState(false);
  const [seenAdds, setSeenAdds] = useState(added);
  if (added !== seenAdds) {
    setSeenAdds(added);
    setShown(true);
  } else if (shown && !panelOpen) {
    setShown(false);
  }

  useEffect(() => {
    if (!shown) {
      return;
    }
    const timer = setTimeout(() => {
      setShown(false);
    }, NOTICE_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [shown, seenAdds]);

  return {
    shown,
    hide: () => {
      setShown(false);
    },
  };
}

/**
 * The side panel of the cart (UC-08): a modal dialog from the right edge over a dimmed page, with the items and the
 * totals as the server sent them. The dialog keeps the focus inside and closes with Escape.
 */
export function CartPanel() {
  const panel = useCartPanel();
  const cart = useCart();
  const navigate = useNavigate();
  const dialog = useRef<HTMLDialogElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const notice = useAddedNotice(panel.added, panel.isOpen);

  // A removed item takes its focused button with it: the focus goes to the close button, not out of the dialog.
  const itemCount = cart.data?.items.length;
  useEffect(() => {
    const element = dialog.current;
    if (element?.open === true && !element.contains(document.activeElement)) {
      closeButton.current?.focus();
    }
  }, [itemCount]);

  return (
    <PanelDialog
      ref={dialog}
      open={panel.isOpen}
      from="right"
      className="cart-panel"
      labelledBy={titleId}
      onDismiss={panel.close}
      onClosed={panel.closed}
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
        {/* Inside the panel, over its title (cart.css): the modal dialog leaves the page inert. */}
        <Toast open={notice.shown} closeLabel={es.cart.closeNotice} onClose={notice.hide}>
          {es.cart.addedNotice}
        </Toast>
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
            <svg className="cart-empty-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M5 8h14l-1.2 12H6.2L5 8z" />
              <path d="M9 10V7a3 3 0 0 1 6 0v3" />
            </svg>
            <p className="cart-empty-title">{es.cart.empty}</p>
            <p>{es.cart.emptyHint}</p>
            <button type="button" className="btn" onClick={panel.close}>
              {es.cart.keepShopping}
            </button>
            {/* T-E7-19: where "Productos sugeridos" goes once it exists. */}
            <div className="cart-suggestions" />
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
              <FreeShippingProgress totalCents={cart.data.totalCents} />
              <CouponField cart={cart.data} />
              <Totals cart={cart.data} />
              <button
                type="button"
                className="btn cart-checkout"
                disabled={!cart.data.items.some((item) => item.status === 'AVAILABLE')}
                onClick={() => {
                  panel.close();
                  void navigate('/checkout');
                }}
              >
                {es.cart.startCheckout}
              </button>
            </footer>
          </>
        )}
      </div>
    </PanelDialog>
  );
}
