import { Link, Outlet } from 'react-router';
import { es } from '../../i18n/es.ts';
import { Analytics, StoreFooter } from '../analytics/ConsentBanner.tsx';
import { HeaderSearch } from './HeaderSearch.tsx';
import './search.css';

/**
 * The frame of every public page: the header with the store name and the search (UC-26), the footer, and the
 * analytics consent (RNF-25), which the back office never shows.
 */
export function StoreLayout() {
  return (
    <>
      <header className="site">
        <div className="wrap header-row">
          <Link to="/" className="logo">
            {es.store.name}
          </Link>
          <HeaderSearch />
        </div>
      </header>
      {/* Before the page, so a returning visitor's tools are loaded when the page sends its first events. */}
      <Analytics />
      <Outlet />
      <StoreFooter />
    </>
  );
}
