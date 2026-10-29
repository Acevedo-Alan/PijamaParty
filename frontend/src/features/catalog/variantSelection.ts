import type { DetailImage, DetailVariant, ProductDetail } from './productDetailApi.ts';

/**
 * The size and color the visitor picked on the detail page (UC-03); null until picked. These helpers only read the
 * variants the server sent: prices and stock are never computed here.
 */
export interface Selection {
  size: string | null;
  color: string | null;
}

export function findVariant(variants: DetailVariant[], { size, color }: Selection): DetailVariant | undefined {
  return variants.find((variant) => variant.size === size && variant.color === color);
}

function matches(variant: DetailVariant, { size, color }: Selection): boolean {
  return (size === null || variant.size === size) && (color === null || variant.color === color);
}

/** Whether some variant of the size, in the picked color if there is one, has available stock. */
export function isSizeAvailable(variants: DetailVariant[], size: string, color: string | null): boolean {
  return variants.some((variant) => matches(variant, { size, color }) && variant.available > 0);
}

/** Whether no variant of the color, in the picked size if there is one, has available stock. */
export function isColorSoldOut(variants: DetailVariant[], color: string, size: string | null): boolean {
  return !variants.some((variant) => matches(variant, { size, color }) && variant.available > 0);
}

/** Whether some variant that matches the picks so far has available stock: false for a sold-out color (T-E7-36). */
export function hasStockFor(variants: DetailVariant[], selection: Selection): boolean {
  return variants.some((variant) => matches(variant, selection) && variant.available > 0);
}

/**
 * A group with a single option starts picked, so a one-size or one-color product needs one choice less. A size
 * without stock is never picked for the visitor.
 */
export function initialSelection({ sizes, colors, variants }: ProductDetail): Selection {
  const color = colors.length === 1 ? (colors[0] ?? null) : null;
  const onlySize = sizes.length === 1 ? sizes[0] : undefined;
  const size = onlySize !== undefined && isSizeAvailable(variants, onlySize, color) ? onlySize : null;
  return { size, color };
}

/** A new color keeps the picked size while that combination has stock; otherwise the size has to be picked again. */
export function pickColor(variants: DetailVariant[], { size }: Selection, color: string): Selection {
  return { color, size: size !== null && isSizeAvailable(variants, size, color) ? size : null };
}

/**
 * The price to show: the picked variant's own price, or the lowest price among the variants that still match the
 * picks, marked as "from" when they differ.
 */
export function shownPrice(variants: DetailVariant[], selection: Selection): { cents: number; from: boolean } | null {
  const prices = variants.filter((variant) => matches(variant, selection)).map((variant) => variant.priceCents);
  if (prices.length === 0) {
    return null;
  }
  const lowest = Math.min(...prices);
  return { cents: lowest, from: prices.some((price) => price !== lowest) };
}

/** The gallery with the photos of the picked color first; the rest keep their position. */
export function galleryFor(images: DetailImage[], color: string | null): DetailImage[] {
  if (color === null) {
    return images;
  }
  return [...images.filter((image) => image.color === color), ...images.filter((image) => image.color !== color)];
}
