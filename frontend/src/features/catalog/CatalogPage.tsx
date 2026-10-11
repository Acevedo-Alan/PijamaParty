import { useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { FilterPanel } from './FilterPanel.tsx';
import { NO_FILTERS, withoutText } from './listingCriteria.ts';
import { ProductListing } from './ProductListing.tsx';
import { SortSelect } from './SortSelect.tsx';
import { useListingCriteria } from './useListingCriteria.ts';
import './catalog.css';

/** The whole shop at / and one category at /categoria/:categorySlug (UC-01), filtered and sorted from the URL (UC-02). */
export function CatalogPage() {
  const { categorySlug } = useParams();
  const [urlCriteria, setCriteria] = useListingCriteria();
  const criteria = withoutText(urlCriteria);
  const title = categorySlug === undefined ? es.catalog.allTitle : es.catalog.categoryTitle;

  return (
    <main className="wrap catalog">
      <title>{`${title} | ${es.store.name}`}</title>
      <h1 className="section-title">{title}</h1>
      <div className="catalog-tools">
        <FilterPanel
          category={categorySlug}
          filters={criteria.filters}
          onApply={(filters) => {
            setCriteria({ ...criteria, filters });
          }}
        />
        <SortSelect
          value={criteria.sort}
          onChange={(sort) => {
            setCriteria({ ...criteria, sort });
          }}
        />
      </div>
      <ProductListing
        category={categorySlug}
        criteria={criteria}
        onClearFilters={() => {
          setCriteria({ filters: NO_FILTERS, sort: criteria.sort });
        }}
      />
    </main>
  );
}
