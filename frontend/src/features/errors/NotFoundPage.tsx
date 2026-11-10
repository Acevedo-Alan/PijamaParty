import { Link } from 'react-router';
import { LazyChibi } from '../../components/chibi/LazyChibi.tsx';
import { es } from '../../i18n/es.ts';
import { NoIndex } from '../seo/NoIndex.tsx';
import './notFound.css';

/**
 * Own 404 page with a way back to the store (RNF-31.7), kept out of search engines (T-E6-23). T-E7-49: the store's
 * chibi, puzzled over an open map, above the heading, all in one centered column; the chibi is decorative, and lazy,
 * since this page is part of the first load.
 */
export function NotFoundPage() {
  return (
    <main className="wrap not-found">
      <title>{`${es.notFound.title} | ${es.store.name}`}</title>
      <NoIndex />
      <LazyChibi mood="puzzled" pose="lost" className="empty-state-chibi" />
      <h1>{es.notFound.title}</h1>
      <p>{es.notFound.body}</p>
      <Link to="/">{es.notFound.backToStore}</Link>
    </main>
  );
}
