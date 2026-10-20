import { Link, useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { SHOW_LISTING } from '../catalog/HomePage.tsx';
import { NO_FILTERS, parseCriteria, toSearchParams, type ProductFilters } from '../catalog/listingCriteria.ts';

/** UC-31: Todo, then the audiences (the UC-02 key `publico`), then the winter season (`temporada`). */
const LINKS: { label: string; filters: ProductFilters }[] = [
  { label: es.header.all, filters: NO_FILTERS },
  { label: es.header.audiences.WOMEN, filters: { ...NO_FILTERS, audiences: ['WOMEN'] } },
  { label: es.header.audiences.MEN, filters: { ...NO_FILTERS, audiences: ['MEN'] } },
  { label: es.header.audiences.KIDS, filters: { ...NO_FILTERS, audiences: ['KIDS'] } },
  { label: es.header.winter, filters: { ...NO_FILTERS, seasons: ['WINTER'] } },
];

/** The URL query of some filters, without a sort: the links always open the default one. */
function filtersQuery(filters: ProductFilters): string {
  return toSearchParams({ filters, sort: 'NEWEST' }).toString();
}

interface StoreNavigationProps {
  className: string;
  /** A link was chosen: the phone menu closes. */
  onChoose?: () => void;
}

/**
 * The store navigation of the header and the phone menu (UC-31). Each link opens the home listing with its filter;
 * the one matching the filters of the home page shown is marked as the current page.
 */
export function StoreNavigation({ className, onChoose }: StoreNavigationProps) {
  const { pathname, search } = useLocation();
  const shown = pathname === '/' ? filtersQuery(parseCriteria(new URLSearchParams(search)).filters) : null;

  return (
    <ul className={className}>
      {LINKS.map(({ label, filters }) => {
        const query = filtersQuery(filters);
        return (
          <li key={label}>
            <Link
              to={{ pathname: '/', search: query }}
              state={query === '' ? undefined : SHOW_LISTING}
              aria-current={query === shown ? 'page' : undefined}
              onClick={onChoose}
            >
              {label}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
