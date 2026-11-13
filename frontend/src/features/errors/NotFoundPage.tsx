import { Link } from 'react-router';
import { LazyChibi } from '../../components/chibi/LazyChibi.tsx';
import { es } from '../../i18n/es.ts';
import { NoIndex } from '../seo/NoIndex.tsx';
import { ErrorPageContact } from './ErrorPageContact.tsx';
import './errorPages.css';

type NotFoundPageProps = {
  /** The back office's own 404 leaves out the store's contact: the one reading it is the owner (RNF-24). */
  withContact?: boolean;
};

/**
 * Own 404 page with a way back to the store and to the contact (RNF-31.7, T-E6-25), kept out of search engines
 * (T-E6-23). T-E7-49: the store's chibi, puzzled over an open map, above the heading, all in one centered column; the
 * chibi is decorative, and lazy, since this page is part of the first load.
 */
export function NotFoundPage({ withContact = true }: NotFoundPageProps) {
  return (
    <main className="wrap error-page">
      <title>{`${es.notFound.title} | ${es.store.name}`}</title>
      <NoIndex />
      <LazyChibi mood="puzzled" pose="lost" className="empty-state-chibi" />
      <h1 data-kaomoji={es.kaomoji.notFound}>{es.notFound.title}</h1>
      <p>{es.notFound.body}</p>
      <Link to="/">{es.notFound.backToStore}</Link>
      {withContact && <ErrorPageContact />}
    </main>
  );
}
