import { Link, Outlet } from 'react-router';
import { es } from '../../i18n/es.ts';
import { HeaderSearch } from './HeaderSearch.tsx';
import './search.css';

/** The header of every public page (UC-26): the store name, back to the shop, and the search. */
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
      <Outlet />
    </>
  );
}
