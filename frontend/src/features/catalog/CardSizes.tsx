import { useRef } from 'react';
import { es } from '../../i18n/es.ts';
import { useCardSizesFont } from './cardSizesFont.ts';
import type { SizeOffer } from './catalogApi.ts';
import { displaySize } from './displayText.ts';
import { sortBySize } from './sizeGroups.ts';

/** T-E7-35 on the card: at most 4 sizes, then "+N", so they keep to the swatches' row on a phone. */
const CARD_SIZES = 4;
/**
 * T-E7-71: and at most this many characters of sizes (the first one always shows), so "+N" fits a phone card's one
 * line beside the swatches: "S · M · L · XL" whole, "6 · 8 · 10 +3" rather than a cut "6 · 8 · 10 · 12 · …".
 */
const CARD_SIZE_CHARACTERS = 5;

/** The first sizes that keep to both limits. */
function fitting(ordered: readonly SizeOffer[]): SizeOffer[] {
  const shown: SizeOffer[] = [];
  let characters = 0;
  for (const offer of ordered.slice(0, CARD_SIZES)) {
    characters += displaySize(offer.size).length;
    if (shown.length > 0 && characters > CARD_SIZE_CHARACTERS) {
      break;
    }
    shown.push(offer);
  }
  return shown;
}

/**
 * The sizes of a product on its card, "S · M · L" (T-E7-35), those without stock struck through. Screen readers hear
 * one sentence with only the sizes in stock, as the swatches say only the colors in stock; none, and "Sold out" says it.
 * Their font, Open Sans, loads once they come near the screen (RNF-11).
 */
export function CardSizes({ sizes }: { sizes: readonly SizeOffer[] }) {
  const row = useRef<HTMLParagraphElement>(null);
  useCardSizesFont(row);
  if (sizes.length === 0) {
    return null;
  }
  const ordered = sortBySize(sizes, (offer) => offer.size);
  const inStock = ordered.filter((offer) => offer.inStock).map((offer) => displaySize(offer.size));
  const shown = fitting(ordered);
  const more = ordered.length - shown.length;

  return (
    <p ref={row} className="card-sizes">
      {inStock.length > 0 && <span className="visually-hidden">{es.catalog.sizes(inStock)}</span>}
      <span className="card-sizes-label" aria-hidden="true">
        {ordered.length === 1 ? es.catalog.sizeLabel : es.catalog.sizesLabel}
      </span>
      {shown.map(({ size, inStock: stocked }) => (
        <span key={size} className={stocked ? undefined : 'out'} aria-hidden="true">
          {displaySize(size)}
        </span>
      ))}
      {more > 0 && <span aria-hidden="true">+{more}</span>}
    </p>
  );
}
