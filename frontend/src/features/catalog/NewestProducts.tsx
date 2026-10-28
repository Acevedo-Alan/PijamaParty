import { useQuery } from '@tanstack/react-query';
import { useId } from 'react';
import { es } from '../../i18n/es.ts';
import { fetchProductPage } from './catalogApi.ts';
import { NO_FILTERS } from './listingCriteria.ts';
import { ProductCard } from './ProductCard.tsx';
import './catalog.css';

/** T-E7-34: how many products show. */
const SHOWN = 4;

/**
 * "Lo más nuevo" (T-E7-34), where a visitor would otherwise hit a dead end (the empty cart, a search without
 * results): the newest listed products not sold out, from the first page of the listing (UC-01). Nothing shows while
 * it loads, when it fails or when no product is left.
 */
export function NewestProducts({ className, onOpen }: { className?: string; onOpen?: () => void }) {
  const titleId = useId();
  const listing = useQuery({
    queryKey: ['catalog', 'newest'],
    queryFn: ({ signal }) => fetchProductPage(undefined, { filters: NO_FILTERS, sort: 'NEWEST' }, 0, signal),
  });
  const page = listing.data;
  const newest = page?.status === 'listed' ? page.items.filter((item) => !item.soldOut).slice(0, SHOWN) : [];
  if (newest.length === 0) {
    return null;
  }

  return (
    <section className={`newest${className === undefined ? '' : ` ${className}`}`} aria-labelledby={titleId}>
      <h2 id={titleId} className="related-title">
        {es.catalog.newest}
      </h2>
      {/* Opening a card may need to close what holds it (the cart panel). The router's links prevent the default of
          every click, so that cannot tell; a swipe stops its click from reaching here instead. */}
      <ul
        className="grid newest-grid"
        onClick={(event) => {
          if (event.target instanceof Element && event.target.closest('a') !== null) {
            onOpen?.();
          }
        }}
      >
        {newest.map((product) => (
          <li key={product.slug}>
            <ProductCard product={product} />
          </li>
        ))}
      </ul>
    </section>
  );
}
