import { es } from '../../i18n/es.ts';
import { EMPTY_CART, unitCount } from './cartApi.ts';
import { useCartPanel } from './CartPanelContext.tsx';
import { useCart } from './useCart.ts';
import './cart.css';

/** The cart button of the header (UC-08): the units in the cart, which bounce after each add. */
export function CartButton() {
  const panel = useCartPanel();
  const count = unitCount(useCart().data ?? EMPTY_CART);

  return (
    <button
      type="button"
      className="icon-btn cart-btn"
      aria-haspopup="dialog"
      aria-label={es.cart.open(count)}
      onClick={panel.open}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6 8h12l-1 12H7L6 8Z" strokeLinejoin="round" />
        <path d="M9 8V6a3 3 0 0 1 6 0v2" />
      </svg>
      {count > 0 && (
        // A new key after each add starts the bounce again.
        <span key={panel.added} className={panel.added > 0 ? 'cart-badge bump' : 'cart-badge'} aria-hidden="true">
          {count}
        </span>
      )}
    </button>
  );
}
