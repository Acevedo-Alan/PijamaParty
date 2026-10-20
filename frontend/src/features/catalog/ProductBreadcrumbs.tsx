import { Link, useLocation, useNavigate } from 'react-router';
import { es } from '../../i18n/es.ts';
import type { ProductDetail } from './productDetailApi.ts';

/**
 * "Volver al catálogo" (E7): back in history when the visitor came from another page of the store, so the listing
 * keeps its filters and scroll; to the home page when the product was opened from outside (a shared link).
 */
export function BackToCatalog() {
  const navigate = useNavigate();
  // React Router names the first entry of the visit "default": there is no store page to go back to.
  const cameFromStore = useLocation().key !== 'default';
  return (
    <Link
      to="/"
      className="back-link"
      onClick={(event) => {
        if (cameFromStore) {
          event.preventDefault();
          void navigate(-1);
        }
      }}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M15 18l-6-6 6-6" />
      </svg>
      {es.product.backToCatalog}
    </Link>
  );
}

/** Inicio / {category} / {product} (E7). */
export function ProductBreadcrumbs({ product }: { product: ProductDetail }) {
  return (
    <nav className="breadcrumbs" aria-label={es.product.breadcrumbs}>
      <ol>
        <li>
          <Link to="/">{es.product.home}</Link>
        </li>
        <li>
          <Link to={`/categoria/${product.category.slug}`}>{product.category.name}</Link>
        </li>
        <li aria-current="page">{product.name}</li>
      </ol>
    </nav>
  );
}
