import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import type { ProductDetail } from './productDetailApi.ts';

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
