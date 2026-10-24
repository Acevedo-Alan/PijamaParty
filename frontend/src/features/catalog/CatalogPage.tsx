import { useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { useShopCategories } from '../search/useShopCategories.ts';
import { PageMeta } from '../seo/PageMeta.tsx';
import { CatalogListing } from './CatalogListing.tsx';
import './catalog.css';

/**
 * One category at /categoria/:categorySlug (UC-01), filtered and sorted from the URL (UC-02). It shows only its
 * listing, without the home sections (UC-30). The tab title and the description carry the category's name once the
 * categories are loaded (T-E6-23).
 */
export function CatalogPage() {
  const { categorySlug } = useParams();
  const title = es.catalog.categoryTitle;
  const category = useShopCategories().data?.find((candidate) => candidate.slug === categorySlug);

  return (
    <main className="wrap catalog">
      <title>{`${category?.name ?? title} | ${es.store.name}`}</title>
      {category !== undefined && <PageMeta description={es.seo.categoryDescription(category.name)} />}
      <CatalogListing category={categorySlug} heading={<h1 className="section-title">{title}</h1>} />
    </main>
  );
}
