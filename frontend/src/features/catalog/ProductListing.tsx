import { m } from 'motion/react';
import { useEffect, useRef, type ReactNode } from 'react';
import { Link } from 'react-router';
import { staggered, useEntranceMotion } from '../../components/motion/useActionMotion.ts';
import { es } from '../../i18n/es.ts';
import { listId, trackItemList } from '../analytics/catalogEvents.ts';
import { countFilters, type ListingCriteria } from './listingCriteria.ts';
import { ShowMore } from './ShowMore.tsx';
import { ProductCard, type CardPhotoPriority } from './ProductCard.tsx';
import type { ListingView } from './useListingView.ts';
import { useProductListing } from './useProductListing.ts';

const SKELETON_CARDS = 8;

/** T-E6-02 (RNF-11): the first row at four columns loads at once, and the first row on a phone comes first. */
const EAGER_CARDS = 4;
const HIGH_PRIORITY_CARDS = 2;

function photoPriority(index: number): CardPhotoPriority {
  if (index < HIGH_PRIORITY_CARDS) {
    return 'high';
  }
  return index < EAGER_CARDS ? 'eager' : 'lazy';
}

function gridClass(view: ListingView): string {
  return view === 'list' ? 'grid list-view' : 'grid';
}

function Skeleton({ view }: { view: ListingView }) {
  return (
    <>
      <p className="visually-hidden" role="status">
        {es.catalog.loading}
      </p>
      <ul className={gridClass(view)} aria-hidden="true">
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
  /** The grid starts below the first screen (the home page, UC-30): its photos load lazily, after the page's own. */
  photosBelowFold?: boolean;
  view?: ListingView;
}

/**
 * D-11: how many products match, for the end of the filter bar row (T-E7-11). It reads the listing's own query, so it
 * shows once the grid has products and never asks the server twice.
 */
export function ResultCount({ category, criteria }: { category: string | undefined; criteria: ListingCriteria }) {
  const listing = useProductListing(category, criteria);
  const pages = listing.data?.pages ?? [];
  const products = pages.reduce((count, page) => count + (page.status === 'listed' ? page.items.length : 0), 0);
  if (products === 0) {
    return null;
  }
  // Every page says how many match; the latest one is the freshest.
  const total = pages.findLast((page) => page.status === 'listed')?.total ?? products;
  return (
    <p className="result-count" role="status">
      {es.catalog.productCount(total)}
    </p>
  );
}

/**
 * The grid of UC-01: 24 products at a time with "Show more", and one message for an empty or unknown category. With
 * filters and no matches it offers to clear them (UC-02). The number of matches is ResultCount.
 */
export function ProductListing({
  category,
  criteria,
  onClearFilters,
  emptyState,
  photosBelowFold = false,
  view = 'grid',
}: ProductListingProps) {
  const listing = useProductListing(category, criteria);
  const entrance = useEntranceMotion();
  const sentPages = useRef(new Set<string>());
  const pages = listing.data?.pages;

  // RNF-25: each page of products shown is one view_item_list, once.
  useEffect(() => {
    if (pages === undefined) {
      return;
    }
    const list = listId(category, criteria.text !== undefined);
    let index = 0;
    pages.forEach((page, number) => {
      const items = page.status === 'listed' ? page.items : [];
      const key = `${JSON.stringify([category ?? null, criteria])}#${String(number)}`;
      if (!sentPages.current.has(key)) {
        sentPages.current.add(key);
        trackItemList(list, items, index);
      }
      index += items.length;
    });
  }, [pages, category, criteria]);

  if (listing.isPending) {
    return <Skeleton view={view} />;
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
  // T-E7-17: each page's cards enter one after another, counted from the start of their page.
  const steps = listing.data.pages.flatMap((page) => (page.status === 'listed' ? page.items.map((_, step) => step) : []));
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
      <ul className={gridClass(view)}>
        {products.map((product, index) => (
          <m.li key={product.slug} {...staggered(entrance, steps[index] ?? 0)}>
            <ProductCard product={product} photo={photosBelowFold ? 'lazy' : photoPriority(index)} view={view} />
          </m.li>
        ))}
      </ul>
      {listing.hasNextPage && (
        <ShowMore
          seen={products.length}
          total={listing.data.pages.findLast((page) => page.status === 'listed')?.total ?? products.length}
          loading={listing.isFetchingNextPage}
          failed={listing.isFetchNextPageError}
          onMore={() => void listing.fetchNextPage()}
        />
      )}
    </>
  );
}
