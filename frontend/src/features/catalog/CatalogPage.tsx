import { useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { CatalogListing } from './CatalogListing.tsx';
import './catalog.css';

/**
 * One category at /categoria/:categorySlug (UC-01), filtered and sorted from the URL (UC-02). It shows only its
 * listing, without the home sections (UC-30).
 */
export function CatalogPage() {
  const { categorySlug } = useParams();
  const title = es.catalog.categoryTitle;

  return (
    <main className="wrap catalog">
      <title>{`${title} | ${es.store.name}`}</title>
      <CatalogListing category={categorySlug} heading={<h1 className="section-title">{title}</h1>} />
    </main>
  );
}
