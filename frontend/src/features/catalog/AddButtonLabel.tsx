import type { ProgressState } from '../../components/ButtonProgress.tsx';
import { es } from '../../i18n/es.ts';
import type { AddAnswer } from '../cart/cartApi.ts';
import { BagIcon } from '../cart/CartButton.tsx';

/** Where the add button is: adding, added for a moment, or rejected or failed (E7 motion). */
export function addButtonState(add: {
  isPending: boolean;
  isError: boolean;
  data: AddAnswer | undefined;
}, confirmed: boolean): ProgressState {
  if (add.isPending) {
    return 'loading';
  }
  if (confirmed) {
    return 'done';
  }
  if (add.isError || (add.data !== undefined && add.data.status !== 'added')) {
    return 'failed';
  }
  return 'idle';
}

/**
 * The inside of the add button: two faces, one over the other. The first ("bag, Agregar al carrito, subtotal") slides
 * up and away when the add is done, and the second (a tick drawn stroke by stroke and "¡Listo! En tu bolsa") comes up
 * from below with a springy ease; the CSS of `.add-cart` does the moving. Only the face that shows is read by screen
 * readers, so the button's name is always what it says.
 */
export function AddButtonLabel({ state, total }: { state: ProgressState; total?: string | undefined }) {
  const done = state === 'done';
  return (
    <>
      <span className="add-cart-face add-cart-idle" aria-hidden={done ? true : undefined}>
        <span className="add-cart-bag">
          <BagIcon />
        </span>
        {state === 'loading' ? es.product.adding : es.product.addToCart}
        {/* The price is already on the page for screen readers, so the button keeps its name. */}
        {total !== undefined && (
          <span className="add-cart-total" aria-hidden="true">
            {total}
          </span>
        )}
      </span>
      <span className="add-cart-face add-cart-done" aria-hidden={done ? undefined : true}>
        <svg className="add-cart-check" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M5 12.5l4.5 4.5L19 7.5" pathLength={1} />
        </svg>
        {es.product.added}
      </span>
    </>
  );
}
