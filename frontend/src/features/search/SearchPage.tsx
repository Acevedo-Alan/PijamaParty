import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { FilterPanel } from '../catalog/FilterPanel.tsx';
import { NO_FILTERS } from '../catalog/listingCriteria.ts';
import { ProductListing } from '../catalog/ProductListing.tsx';
import { SortSelect } from '../catalog/SortSelect.tsx';
import { useListingCriteria } from '../catalog/useListingCriteria.ts';
import { fetchShopCategories } from './searchApi.ts';
import { isSearchable } from './useSuggestions.ts';
import '../catalog/catalog.css';

/** Nothing matched the text: links to the categories and to the whole catalog (UC-26). */
function NoResults({ text }: { text: string }) {
  const categories = useQuery({
    queryKey: ['catalog', 'categories'],
    queryFn: ({ signal }) => fetchShopCategories(signal),
    staleTime: 300_000,
  });
  return (
    <div className="empty-state">
      <p>{es.search.noResults(text)}</p>
      <nav aria-label={es.search.categories}>
        <ul className="category-links">
          {(categories.data ?? []).map((category) => (
            <li key={category.slug}>
              <Link to={`/categoria/${category.slug}`}>{category.name}</Link>
            </li>
          ))}
          <li>
            <Link to="/">{es.catalog.seeAll}</Link>
          </li>
        </ul>
      </nav>
    </div>
  );
}

/** The results of a search at /buscar?q= (UC-26), with the filters and sorts of UC-02 and relevance first. */
export function SearchPage() {
  const [criteria, setCriteria] = useListingCriteria();
  const { text } = criteria;

  if (!isSearchable(text)) {
    return (
      <main className="wrap catalog">
        <title>{`${es.search.title} | ${es.store.name}`}</title>
        <h1 className="section-title">{es.search.title}</h1>
        <p>{es.search.tooShort}</p>
      </main>
    );
  }

  const title = es.search.resultsFor(text);
  return (
    <main className="wrap catalog">
      <title>{`${title} | ${es.store.name}`}</title>
      <h1 className="section-title">{title}</h1>
      <div className="catalog-tools">
        <FilterPanel
          category={undefined}
          filters={criteria.filters}
          onApply={(filters) => {
            setCriteria({ ...criteria, filters });
          }}
        />
        <SortSelect
          searching
          value={criteria.sort}
          onChange={(sort) => {
            setCriteria({ ...criteria, sort });
          }}
        />
      </div>
      <ProductListing
        category={undefined}
        criteria={criteria}
        emptyState={<NoResults text={text} />}
        onClearFilters={() => {
          setCriteria({ filters: NO_FILTERS, sort: criteria.sort, text });
        }}
      />
    </main>
  );
}
