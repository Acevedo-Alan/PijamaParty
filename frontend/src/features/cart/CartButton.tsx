import { m } from 'motion/react';
import { useActionMotion } from '../../components/motion/useActionMotion.ts';
import { es } from '../../i18n/es.ts';
import { duration, ease } from '../../styles/motion.ts';
import { EMPTY_CART, unitCount } from './cartApi.ts';
import { useCartPanel } from './CartPanelContext.tsx';
import { useCart } from './useCart.ts';
import './cart.css';

/** The cart's bag, drawn in line like the store's other icons; the phone menu's "Mi carrito" carries it too. */
export function BagIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M6 8h12l-1 12H7L6 8Z" strokeLinejoin="round" />
      <path d="M9 8V6a3 3 0 0 1 6 0v2" />
    </svg>
  );
}

/** The cart button of the header (UC-08): the units in the cart, which bump once after each add. */
export function CartButton() {
  const panel = useCartPanel();
  const count = unitCount(useCart().data ?? EMPTY_CART);
  const bump = useActionMotion({
    hidden: { scale: 1.5 },
    shown: { scale: 1 },
    transition: { duration: duration.base, ease: ease.pop },
  });

  return (
    <button
      type="button"
      className="icon-btn cart-btn"
      aria-haspopup="dialog"
      aria-label={es.cart.open(count)}
      onClick={panel.open}
    >
      <BagIcon />
      {count > 0 && (
        // A new key after each add mounts the counter again, so it bumps once.
        <m.span key={panel.added} className="cart-badge" aria-hidden="true" {...(panel.added > 0 ? bump : {})}>
          {count}
        </m.span>
      )}
    </button>
  );
}
