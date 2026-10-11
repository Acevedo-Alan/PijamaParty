import { useId } from 'react';
import { es } from '../../i18n/es.ts';
import type { ProductSort } from './listingCriteria.ts';

const SORTS: readonly ProductSort[] = ['NEWEST', 'PRICE_ASC', 'PRICE_DESC'];

function isSort(value: string): value is ProductSort {
  return (SORTS as readonly string[]).includes(value);
}

/** Newest first or by "from" price (UC-02); a new sort applies at once. */
export function SortSelect({ value, onChange }: { value: ProductSort; onChange: (sort: ProductSort) => void }) {
  const id = useId();
  return (
    <div className="sort">
      <label htmlFor={id}>{es.sort.label}</label>
      <select
        id={id}
        value={value}
        onChange={(event) => {
          if (isSort(event.target.value)) {
            onChange(event.target.value);
          }
        }}
      >
        {SORTS.map((sort) => (
          <option key={sort} value={sort}>
            {es.sort[sort]}
          </option>
        ))}
      </select>
    </div>
  );
}
