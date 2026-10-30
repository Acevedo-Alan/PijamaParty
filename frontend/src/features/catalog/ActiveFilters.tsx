import { useEffect, useRef } from 'react';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { countFilters, NO_FILTERS, type ProductFilters } from './listingCriteria.ts';

interface Chip {
  key: string;
  label: string;
  without: ProductFilters;
}

/** The price range as the chip and the panel's header say it, or null without one. */
export function priceLabel({ minPriceCents: min, maxPriceCents: max }: ProductFilters): string | null {
  if (min !== undefined && max !== undefined) {
    return es.filters.priceBetween(formatMoney(min), formatMoney(max));
  }
  if (min !== undefined) {
    return es.filters.priceFrom(formatMoney(min));
  }
  return max === undefined ? null : es.filters.priceUpTo(formatMoney(max));
}

function chips(filters: ProductFilters): Chip[] {
  const values = <K extends 'sizes' | 'colors' | 'materials' | 'seasons' | 'audiences'>(
    group: K,
    label: (value: ProductFilters[K][number]) => string,
  ): Chip[] =>
    (filters[group] as ProductFilters[K][number][]).map((value) => ({
      key: `${group}:${value}`,
      label: label(value),
      without: { ...filters, [group]: filters[group].filter((other) => other !== value) },
    }));
  const price = priceLabel(filters);
  const withoutPrice: ProductFilters = { ...filters };
  delete withoutPrice.minPriceCents;
  delete withoutPrice.maxPriceCents;
  return [
    ...values('audiences', (audience) => es.filters.audiences[audience]),
    ...values('sizes', es.filters.sizeChip),
    ...values('colors', (color) => color),
    ...values('materials', (material) => material),
    ...values('seasons', (season) => es.filters.seasons[season]),
    ...(price === null ? [] : [{ key: 'price', label: price, without: withoutPrice }]),
  ];
}

/** The first button of the filter bar, which stays on the page when no filter is left. */
function findFilterButton(): HTMLElement | null {
  return document.querySelector<HTMLElement>('.filters-bar .filter-btn');
}

interface ActiveFiltersProps {
  filters: ProductFilters;
  onApply: (filters: ProductFilters) => void;
}

/**
 * The applied filters as chips above the grid (changes to UC-02): each one removes its value at once, and "Clear all"
 * removes them all; the sort stays. Nothing shows without filters.
 */
export function ActiveFilters({ filters, onApply }: ActiveFiltersProps) {
  const row = useRef<HTMLDivElement>(null);
  /**
   * The position of the removed chip and how many chips there were: once fewer show, the focus goes to the chip now
   * at that position, since the pressed button left with its value.
   */
  const refocus = useRef<{ index: number; before: number } | null>(null);
  const applied = chips(filters);

  // WCAG 2.4.3: the next chip, the previous one after the last, or the filter bar once no chip is left.
  useEffect(() => {
    if (refocus.current === null || applied.length >= refocus.current.before) {
      return;
    }
    const remaining = row.current?.querySelectorAll<HTMLElement>('.active-chip') ?? [];
    const target = remaining[Math.min(refocus.current.index, remaining.length - 1)] ?? findFilterButton();
    refocus.current = null;
    target?.focus();
  });

  if (applied.length === 0) {
    return null;
  }

  function remove(index: number, without: ProductFilters) {
    refocus.current = { index, before: applied.length };
    onApply(without);
  }

  return (
    <div ref={row} className="active-filters" role="group" aria-label={es.filters.applied}>
      <ul>
        {applied.map((chip, index) => (
          <li key={chip.key}>
            <button
              type="button"
              className="active-chip"
              aria-label={es.filters.removeChip(chip.label)}
              onClick={() => {
                remove(index, chip.without);
              }}
            >
              {chip.label}
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M7 7l10 10M17 7L7 17" />
              </svg>
            </button>
          </li>
        ))}
      </ul>
      {countFilters(filters) > 1 && (
        <button
          type="button"
          className="link-btn"
          onClick={() => {
            remove(0, NO_FILTERS);
          }}
        >
          {es.filters.clearAll}
        </button>
      )}
    </div>
  );
}
