import { es } from '../../i18n/es.ts';
import type { ProductDetail } from '../catalog/productDetailApi.ts';
import { REVIEWS_SHOWN } from '../reviews/reviewsShown.ts';
import { useProductReviews } from '../reviews/useProductReviews.ts';
import { canonicalUrl } from './PageMeta.tsx';
import { oneLine } from './productMeta.ts';

/** UC-24's average and count, when the product has approved reviews. */
export interface Rating {
  average: number;
  count: number;
}

/** `12500.00`: pesos with two decimals and a point, as schema.org writes prices. */
function amount(cents: number): string {
  return (cents / 100).toFixed(2);
}

/**
 * The schema.org data of a product page (T-E6-23, RNF-31.2): one Product with one Offer per variant, and its rating
 * only when it has approved reviews.
 */
export function productStructuredData(product: ProductDetail, rating: Rating | null): object {
  const url = canonicalUrl(`/producto/${product.slug}`);
  const description = oneLine(product.description);
  return {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: product.name,
    ...(description === '' ? {} : { description }),
    url,
    ...(product.images.length === 0 ? {} : { image: product.images.map((image) => canonicalUrl(image.largeSrc)) }),
    brand: { '@type': 'Brand', name: es.store.name },
    offers: product.variants.map((variant) => ({
      '@type': 'Offer',
      sku: variant.sku,
      price: amount(variant.priceCents),
      priceCurrency: 'ARS',
      availability: variant.available > 0 ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock',
      url,
    })),
    ...(rating === null
      ? {}
      : { aggregateRating: { '@type': 'AggregateRating', ratingValue: rating.average, reviewCount: rating.count } }),
  };
}

/**
 * JSON inside a script element. React sets it as text; "<" is still written as <, so no text of the product
 * could close the element if the page were ever written as HTML.
 */
export function scriptJson(data: object): string {
  return JSON.stringify(data).replace(/</g, '\\u003c');
}

function StructuredDataScript({ product, rating }: { product: ProductDetail; rating: Rating | null }) {
  return <script type="application/ld+json">{scriptJson(productStructuredData(product, rating))}</script>;
}

function RatedStructuredData({ product }: { product: ProductDetail }) {
  const reviews = useProductReviews(product.slug);
  const first = reviews.data?.pages[0];
  const rating =
    first !== undefined && first !== null && first.average !== null && first.count > 0
      ? { average: first.average, count: first.count }
      : null;
  return <StructuredDataScript product={product} rating={rating} />;
}

/**
 * The product's structured data; the content security policy does not apply to it, since it is never run. While the
 * store hides the reviews (reviewsShown.ts) it has no rating either, since search engines may show only what the page
 * shows.
 */
export function ProductStructuredData({ product }: { product: ProductDetail }) {
  return REVIEWS_SHOWN ? (
    <RatedStructuredData product={product} />
  ) : (
    <StructuredDataScript product={product} rating={null} />
  );
}
