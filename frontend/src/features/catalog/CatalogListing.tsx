import type { ReactNode } from 'react';
import { FilterPanel } from './FilterPanel.tsx';
import { NO_FILTERS, withoutText } from './listingCriteria.ts';
import { ProductListing } from './ProductListing.tsx';
import { SortSelect } from './SortSelect.tsx';
import { useListingCriteria } from './useListingCriteria.ts';
import { useListingView } from './useListingView.ts';
import { ViewToggle } from './ViewToggle.tsx';
import './catalog.css';

interface CatalogListingProps {
  /** The whole shop when absent. */
  category?: string | undefined;
  /** The listing's title: the page's h1 for a category, a section heading on the home page. */
  heading: ReactNode;
  /** On the home page the grid starts below the first screen: none of its photos load first (RNF-11). */
  photosBelowFold?: boolean;
}

/**
 * The listing of UC-01 with the filters and sort of UC-02, read from and written to the URL, and the grid or list
 * view of the visit. The home page and a category page share it (UC-30).
 */
export function CatalogListing({ category, heading, photosBelowFold = false }: CatalogListingProps) {
  const [urlCriteria, setCriteria] = useListingCriteria();
  const criteria = withoutText(urlCriteria);
  const [view, setView] = useListingView();

  return (
    <>
      {heading}
      <FilterPanel
        category={category}
        filters={criteria.filters}
        onApply={(filters) => {
          setCriteria({ ...criteria, filters });
        }}
      />
      <div className="catalog-tools">
        <SortSelect
          value={criteria.sort}
          onChange={(sort) => {
            setCriteria({ ...criteria, sort });
          }}
        />
        <ViewToggle value={view} onChange={setView} />
      </div>
      <ProductListing
        category={category}
        criteria={criteria}
        photosBelowFold={photosBelowFold}
        view={view}
        onClearFilters={() => {
          setCriteria({ filters: NO_FILTERS, sort: criteria.sort });
        }}
      />
    </>
  );
}
