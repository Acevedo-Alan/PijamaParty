import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { NoIndex } from '../seo/NoIndex.tsx';

/**
 * Shown by the root `errorElement` when a page fails to load or render, most often a lazy chunk that no longer
 * exists after a deploy (T-E8-21). Reloading fetches the new build.
 */
export function RouteErrorPage() {
  return (
    <main className="wrap">
      <NoIndex />
      <h1>{es.routeError.title}</h1>
      <p>{es.routeError.body}</p>
      <p>
        <button type="button" className="btn" onClick={() => {
            window.location.reload();
          }}>
          {es.routeError.reload}
        </button>{' '}
        <Link to="/">{es.notFound.backToStore}</Link>
      </p>
    </main>
  );
}
