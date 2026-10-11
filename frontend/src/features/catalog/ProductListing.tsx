import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { countFilters, type ListingCriteria } from './listingCriteria.ts';
import { ProductCard } from './ProductCard.tsx';
import { useProductListing } from './useProductListing.ts';

const SKELETON_CARDS = 8;

function Skeleton() {
  return (
    <>
      <p className="visually-hidden" role="status">
        {es.catalog.loading}
      </p>
      <ul className="grid" aria-hidden="true">
        {Array.from({ length: SKELETON_CARDS }, (_, index) => (
          <li key={index} className="skeleton-card">
            <div className="skeleton-ph" />
            <div className="skeleton-text" />
            <div className="skeleton-text short" />
          </li>
        ))}
      </ul>
    </>
  );
}

interface ProductListingProps {
  category: string | undefined;
  criteria: ListingCriteria;
  onClearFilters: () => void;
  /** Shown instead of the empty-shop or empty-category message when nothing matches without filters (UC-26). */
  emptyState?: ReactNode;
}

/**
 * The grid of UC-01: 24 products at a time with "Show more", and one message for an empty or unknown category.
 * With filters and no matches it offers to clear them (UC-02).
 */
export function ProductListing({ category, criteria, onClearFilters, emptyState }: ProductListingProps) {
  const listing = useProductListing(category, criteria);

  if (listing.isPending) {
    return <Skeleton />;
  }
  // A failed "Show more" keeps the loaded pages; only a failed first load replaces the grid.
  if (listing.isLoadingError) {
    return (
      <div className="empty-state" role="alert">
        <p>{es.catalog.loadError}</p>
        <button type="button" className="btn" onClick={() => void listing.refetch()}>
          {es.catalog.retry}
        </button>
      </div>
    );
  }

  const products = listing.data.pages.flatMap((page) => (page.status === 'listed' ? page.items : []));
  const unknownCategory = listing.data.pages[0]?.status === 'unknownCategory';
  if (products.length === 0 && !unknownCategory && countFilters(criteria.filters) > 0) {
    return (
      <div className="empty-state">
        <p>{es.catalog.noMatches}</p>
        <button type="button" className="btn" onClick={onClearFilters}>
          {es.catalog.clearFilters}
        </button>
      </div>
    );
  }
  if (products.length === 0 && emptyState !== undefined && !unknownCategory) {
    return emptyState;
  }
  if (products.length === 0) {
    return category === undefined ? (
      <div className="empty-state">
        <p>{es.catalog.emptyShop}</p>
      </div>
    ) : (
      <div className="empty-state">
        <p>{es.catalog.emptyCategory}</p>
        <Link to="/">{es.catalog.seeAll}</Link>
      </div>
    );
  }

  return (
    <>
      <ul className="grid">
        {products.map((product) => (
          <li key={product.slug}>
            <ProductCard product={product} />
          </li>
        ))}
      </ul>
      {listing.hasNextPage && (
        <div className="show-more">
          {listing.isFetchNextPageError && (
            <p className="show-more-error" role="alert">
              {es.catalog.loadMoreError}
            </p>
          )}
          <button
            type="button"
            className="btn"
            disabled={listing.isFetchingNextPage}
            onClick={() => void listing.fetchNextPage()}
          >
            {listing.isFetchingNextPage ? es.catalog.loadingMore : es.catalog.showMore}
          </button>
        </div>
      )}
    </>
  );
}
