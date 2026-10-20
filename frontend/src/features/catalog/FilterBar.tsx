import { es } from '../../i18n/es.ts';
import type { FilterOptions } from './catalogApi.ts';
import type { ProductFilters } from './listingCriteria.ts';

/** A group of the filters panel (UC-02), as the bar names it. */
export type FilterGroup = 'audiences' | 'sizes' | 'colors' | 'materials' | 'seasons' | 'price';

/** The bar's order (T-E7-06); the panel keeps the order of UC-02. */
const BAR_GROUPS: readonly FilterGroup[] = ['audiences', 'sizes', 'colors', 'materials', 'seasons', 'price'];

const LABELS: Record<FilterGroup, string> = {
  audiences: es.filters.audience,
  sizes: es.filters.size,
  colors: es.filters.color,
  materials: es.filters.material,
  seasons: es.filters.season,
  price: es.filters.price,
};

/** How many values of a group are chosen; the price range counts once. */
function chosen(group: FilterGroup, filters: ProductFilters): number {
  if (group === 'price') {
    return filters.minPriceCents !== undefined || filters.maxPriceCents !== undefined ? 1 : 0;
  }
  return filters[group].length;
}

function hasOptions(group: FilterGroup, options: FilterOptions): boolean {
  return group === 'price' ? options.priceRange !== null : options[group].length > 0;
}

interface FilterBarProps {
  /** Absent while the options load or after they fail: every button shows, and the panel says why it is empty. */
  options: FilterOptions | undefined;
  filters: ProductFilters;
  onOpen: (group: FilterGroup) => void;
}

/**
 * The buttons that open the filters panel at one group (changes to UC-02). A button says how many values of its
 * group are chosen; a group without options has no button, unless a shared link chose a value in it.
 */
export function FilterBar({ options, filters, onOpen }: FilterBarProps) {
  const groups = BAR_GROUPS.filter(
    (group) => options === undefined || hasOptions(group, options) || chosen(group, filters) > 0,
  );
  if (groups.length === 0) {
    return null;
  }
  return (
    <div className="filters-bar" role="group" aria-label={es.filters.bar}>
      {groups.map((group) => {
        const count = chosen(group, filters);
        return (
          <button
            key={group}
            type="button"
            className={count === 0 ? 'filter-btn' : 'filter-btn active'}
            aria-haspopup="dialog"
            onClick={() => {
              onOpen(group);
            }}
          >
            {es.filters.groupWithCount(LABELS[group], count)}
          </button>
        );
      })}
    </div>
  );
}
