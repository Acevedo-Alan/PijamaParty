import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { NoIndex } from '../seo/NoIndex.tsx';
import { ErrorPageContact } from './ErrorPageContact.tsx';
import './errorPages.css';

/**
 * The store's 500 page (RNF-31.7, T-E6-25), shown by the root `errorElement` when a page fails to load or render, most
 * often a lazy chunk that no longer exists after a deploy (T-E8-21). Reloading fetches the new build; the links lead
 * back to the store and to the contact.
 */
export function RouteErrorPage() {
  return (
    <main className="wrap error-page">
      <title>{`${es.routeError.title} | ${es.store.name}`}</title>
      <NoIndex />
      <h1>{es.routeError.title}</h1>
      <p>{es.routeError.body}</p>
      <div className="error-page-actions">
        <button type="button" className="btn" onClick={() => {
            window.location.reload();
          }}>
          {es.routeError.reload}
        </button>
        <Link to="/">{es.notFound.backToStore}</Link>
      </div>
      <ErrorPageContact />
    </main>
  );
}
