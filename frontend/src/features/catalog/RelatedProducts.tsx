import { useQuery } from '@tanstack/react-query';
import { useId } from 'react';
import { es } from '../../i18n/es.ts';
import { newestPageQuery } from './newestPage.ts';
import { ProductCard } from './ProductCard.tsx';

/** D-08: at most this many products besides the open one. */
const RELATED_COUNT = 4;

/**
 * "Completá tu look" (D-08): the newest products of the category without the open one, from the first page of the
 * listing (UC-01). Nothing shows while it loads, when it fails or when the category has no other product.
 */
export function RelatedProducts({ category, slug }: { category: string; slug: string }) {
  const listing = useQuery(newestPageQuery(category));
  const titleId = useId();
  const page = listing.data;
  const related =
    page?.status === 'listed' ? page.items.filter((item) => item.slug !== slug).slice(0, RELATED_COUNT) : [];
  if (related.length === 0) {
    return null;
  }

  return (
    <section className="related" aria-labelledby={titleId}>
      <h2 id={titleId} className="related-title">
        {es.product.related}
      </h2>
      <ul className="product-grid">
        {related.map((product) => (
          <li key={product.slug}>
            <ProductCard product={product} />
          </li>
        ))}
      </ul>
    </section>
  );
}
