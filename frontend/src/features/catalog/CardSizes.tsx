import { es } from '../../i18n/es.ts';
import type { SizeOffer } from './catalogApi.ts';

/** T-E7-35 on the card: at most 6 sizes, then "+N". */
const CARD_SIZES = 6;

/**
 * The sizes of a product on its card, "S · M · L" (T-E7-35), those without stock struck through. Screen readers hear
 * one sentence with only the sizes in stock, as the swatches say only the colors in stock; none, and "Sold out" says it.
 */
export function CardSizes({ sizes }: { sizes: readonly SizeOffer[] }) {
  if (sizes.length === 0) {
    return null;
  }
  const inStock = sizes.filter((offer) => offer.inStock).map((offer) => offer.size);
  const shown = sizes.slice(0, CARD_SIZES);
  const more = sizes.length - shown.length;

  return (
    <p className="card-sizes">
      {inStock.length > 0 && <span className="visually-hidden">{es.catalog.sizes(inStock)}</span>}
      {shown.map(({ size, inStock: stocked }) => (
        <span key={size} className={stocked ? undefined : 'out'} aria-hidden="true">
          {size}
        </span>
      ))}
      {more > 0 && <span aria-hidden="true">+{more}</span>}
    </p>
  );
}
