import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';

/** Own 404 page with a way back to the store (RNF-31.7). */
export function NotFoundPage() {
  return (
    <main className="wrap">
      <h1>{es.notFound.title}</h1>
      <p>{es.notFound.body}</p>
      <Link to="/">{es.notFound.backToStore}</Link>
    </main>
  );
}
