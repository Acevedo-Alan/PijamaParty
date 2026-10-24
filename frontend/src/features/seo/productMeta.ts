import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { ProductDetail } from '../catalog/productDetailApi.ts';

/** The longest description in the page's description, as in the link preview (T-E6-07). */
const DESCRIPTION_LENGTH = 200;

/** The description on one line; empty when there is none. */
export function oneLine(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}

/** The description on one line, cut at a word to at most 200 characters with "…", as the preview cuts it. */
export function cutDescription(text: string): string {
  const line = oneLine(text);
  if (line.length <= DESCRIPTION_LENGTH) {
    return line;
  }
  let cut = line.slice(0, DESCRIPTION_LENGTH - 1);
  const lastSpace = cut.lastIndexOf(' ');
  if (lastSpace > 0) {
    cut = cut.slice(0, lastSpace);
  }
  return `${cut.replace(/[\s.,;:-]+$/, '')}…`;
}

/** The product page's description (T-E6-23): its price, with "Desde" when prices differ, then its description. */
export function productDescription(product: ProductDetail): string {
  const price = formatMoney(product.fromPriceCents);
  const onePrice = product.variants.every((variant) => variant.priceCents === product.fromPriceCents);
  return es.seo.productDescription(
    onePrice ? price : `${es.catalog.fromPrice} ${price}`,
    cutDescription(product.description),
  );
}
