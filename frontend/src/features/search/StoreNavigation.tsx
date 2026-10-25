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

export interface StoreLink {
  label: string;
  /** The home page with the link's filter. */
  search: string;
  /** It matches the filters of the home page shown. */
  current: boolean;
}

/** The links of the store navigation, so the header and the phone menu can each lay them out (UC-31). */
export function useStoreLinks(): StoreLink[] {
  const { pathname, search } = useLocation();
  const shown = pathname === '/' ? filtersQuery(parseCriteria(new URLSearchParams(search)).filters) : null;
  return LINKS.map(({ label, filters }) => {
    const query = filtersQuery(filters);
    return { label, search: query, current: query === shown };
  });
}

/**
 * One link of the store navigation: it opens the home listing with its filter, and is marked as the current page
 * when that listing is shown.
 */
export function StoreNavigationLink({ link, onChoose }: { link: StoreLink; onChoose?: (() => void) | undefined }) {
  return (
    <Link
      to={{ pathname: '/', search: link.search }}
      state={link.search === '' ? undefined : SHOW_LISTING}
      aria-current={link.current ? 'page' : undefined}
      onClick={onChoose}
    >
      {link.label}
    </Link>
  );
}

/** The store navigation of the header (UC-31); the phone menu lays out the same links with its entrance. */
export function StoreNavigation({ className }: { className: string }) {
  return (
    <ul className={className}>
      {useStoreLinks().map((link) => (
        <li key={link.label}>
          <StoreNavigationLink link={link} />
        </li>
      ))}
    </ul>
  );
}
