import { Link, useLocation } from 'react-router';
import { SHOW_LISTING } from '../catalog/HomePage.tsx';
import { parseCriteria } from '../catalog/listingCriteria.ts';
import { filtersQuery, STORE_LINKS } from '../catalog/storeLinks.ts';

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
  return STORE_LINKS.map(({ label, filters }) => {
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
