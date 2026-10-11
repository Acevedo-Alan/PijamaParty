import { useId } from 'react';
import { es } from '../../i18n/es.ts';
import type { ProductSort } from './listingCriteria.ts';

const LISTING_SORTS: readonly ProductSort[] = ['NEWEST', 'PRICE_ASC', 'PRICE_DESC'];
const SEARCH_SORTS: readonly ProductSort[] = ['RELEVANCE', 'NEWEST', 'PRICE_ASC', 'PRICE_DESC'];

interface SortSelectProps {
  value: ProductSort;
  onChange: (sort: ProductSort) => void;
  /** A search also offers relevance (UC-26). */
  searching?: boolean;
}

/** Newest first or by "from" price (UC-02), and by relevance in a search (UC-26); a new sort applies at once. */
export function SortSelect({ value, onChange, searching = false }: SortSelectProps) {
  const id = useId();
  const sorts = searching ? SEARCH_SORTS : LISTING_SORTS;
  return (
    <div className="sort">
      <label htmlFor={id}>{es.sort.label}</label>
      <select
        id={id}
        value={value}
        onChange={(event) => {
          const sort = sorts.find((option) => option === event.target.value);
          if (sort !== undefined) {
            onChange(sort);
          }
        }}
      >
        {sorts.map((sort) => (
          <option key={sort} value={sort}>
            {es.sort[sort]}
          </option>
        ))}
      </select>
    </div>
  );
}
