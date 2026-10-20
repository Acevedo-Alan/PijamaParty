import { Link } from 'react-router';
import { Collapsible } from '../../components/Collapsible.tsx';
import { es } from '../../i18n/es.ts';
import { FAQ_PATH } from '../help/FaqLink.tsx';
import type { ProductDetail } from './productDetailApi.ts';
import { SizeGuideSection } from './SizeGuideSection.tsx';

/**
 * E7: the collapsed sections below the add button: the size guide when the category has one (RNF-31.5), the
 * composition, and exchanges and returns with the link to the FAQ.
 */
export function ProductSections({ product }: { product: ProductDetail }) {
  return (
    <div className="pdp-sections">
      {product.sizeGuide !== null && <SizeGuideSection guide={product.sizeGuide} />}
      <Collapsible title={es.product.composition} className="pdp-section">
        <dl className="pdp-facts">
          {product.material !== '' && (
            <div>
              <dt>{es.product.material}</dt>
              <dd>{product.material}</dd>
            </div>
          )}
          <div>
            <dt>{es.product.season}</dt>
            <dd>{es.filters.seasons[product.season]}</dd>
          </div>
        </dl>
      </Collapsible>
      <Collapsible title={es.product.returns} className="pdp-section">
        <p>{es.product.returnsText}</p>
        <Link to={FAQ_PATH}>{es.product.returnsLink}</Link>
      </Collapsible>
    </div>
  );
}
