import { es } from '../../i18n/es.ts';
import { NO_FILTERS, toSearchParams, type ProductFilters } from './listingCriteria.ts';

/** A link of the store navigation (UC-31): the home listing with one filter, or none for "Todo". */
export interface StoreLinkTarget {
  label: string;
  filters: ProductFilters;
}

/** UC-31: Todo, then the audiences (the UC-02 key `publico`), then the winter season (`temporada`). */
export const STORE_LINKS: readonly StoreLinkTarget[] = [
  { label: es.header.all, filters: NO_FILTERS },
  { label: es.header.audiences.WOMEN, filters: { ...NO_FILTERS, audiences: ['WOMEN'] } },
  { label: es.header.audiences.MEN, filters: { ...NO_FILTERS, audiences: ['MEN'] } },
  { label: es.header.audiences.KIDS, filters: { ...NO_FILTERS, audiences: ['KIDS'] } },
  { label: es.header.winter, filters: { ...NO_FILTERS, seasons: ['WINTER'] } },
];

/** The URL query of some filters, without a sort: the links always open the default one. */
export function filtersQuery(filters: ProductFilters): string {
  return toSearchParams({ filters, sort: 'NEWEST' }).toString();
}

/**
 * T-E7-76: the header link whose filter is exactly the one applied ("Hombre"), so the listing can name it; nothing
 * without filters ("Todo" is the whole shop) or with any other combination.
 */
export function storeLinkLabel(filters: ProductFilters): string | undefined {
  const query = filtersQuery(filters);
  return query === '' ? undefined : STORE_LINKS.find((link) => filtersQuery(link.filters) === query)?.label;
}
