import { FilterPanel } from './FilterPanel.tsx';
import type { ListingCriteria, ProductFilters, ProductSort } from './listingCriteria.ts';
import { ResultCount } from './ProductListing.tsx';
import { SortSelect } from './SortSelect.tsx';
import { useProductListing } from './useProductListing.ts';
import type { ListingView } from './useListingView.ts';
import { ViewToggle } from './ViewToggle.tsx';

interface ListingToolbarProps {
  category: string | undefined;
  criteria: ListingCriteria;
  /** The search page offers relevance first (UC-26). */
  searching?: boolean;
  view: ListingView;
  onFilters: (filters: ProductFilters) => void;
  onSort: (sort: ProductSort) => void;
  onView: (view: ListingView) => void;
}

/** Whether the listing has loaded with nothing in it: then there is nothing to order or lay out (T-E7-76). */
function useLoadedEmpty(category: string | undefined, criteria: ListingCriteria): boolean {
  const listing = useProductListing(category, criteria);
  return listing.isSuccess && listing.data.pages.every((page) => page.status !== 'listed' || page.items.length === 0);
}

/**
 * The filter bar, the applied filters, the count, the sort and the view toggle of a listing as one block (T-E7-76),
 * shared by the home page, a category page and the search page. From 860 px the buttons share their row with the
 * sort and the view, and the count leads the chips' row; below, the rows of T-E7-70 stay (catalog.css).
 */
export function ListingToolbar({
  category,
  criteria,
  searching = false,
  view,
  onFilters,
  onSort,
  onView,
}: ListingToolbarProps) {
  const empty = useLoadedEmpty(category, criteria);
  return (
    <div className="listing-toolbar">
      <FilterPanel category={category} filters={criteria.filters} onApply={onFilters} />
      <ResultCount category={category} criteria={criteria} />
      {!empty && (
        <>
          <SortSelect searching={searching} value={criteria.sort} onChange={onSort} />
          <ViewToggle value={view} onChange={onView} />
        </>
      )}
    </div>
  );
}
