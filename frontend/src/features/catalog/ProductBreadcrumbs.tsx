import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import type { ProductDetail } from './productDetailApi.ts';

/**
 * Inicio / {category} / {product} (E7). On a phone they are pastel pills on one line (T-E7-51): Inicio shows a house,
 * its word kept for screen readers, so the link's name stays "Inicio".
 */
export function ProductBreadcrumbs({ product }: { product: ProductDetail }) {
  return (
    <nav className="breadcrumbs" aria-label={es.product.breadcrumbs}>
      <ol>
        <li>
          <Link to="/" className="breadcrumbs-home">
            <svg className="breadcrumbs-home-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="M4 11.5 12 5l8 6.5M6.5 10v9h11v-9M10 19v-5h4v5" />
            </svg>
            <span className="breadcrumbs-home-text">{es.product.home}</span>
          </Link>
        </li>
        <li>
          <Link to={`/categoria/${product.category.slug}`}>{product.category.name}</Link>
        </li>
        <li aria-current="page">
          <span className="breadcrumbs-current">{product.name}</span>
        </li>
      </ol>
    </nav>
  );
}
