import { useEffect, useId, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { trackItemView } from '../analytics/catalogEvents.ts';
import { NotFoundPage } from '../errors/NotFoundPage.tsx';
import { ProductGallery } from './ProductGallery.tsx';
import type { ProductDetail } from './productDetailApi.ts';
import { useProductDetail } from './useProductDetail.ts';
import { VariantPicker } from './VariantPicker.tsx';
import { findVariant, galleryFor, initialSelection, pickColor, shownPrice } from './variantSelection.ts';
import './catalog.css';
import './productDetail.css';

function StockNote({ available }: { available: number | undefined }) {
  if (available === undefined) {
    return null;
  }
  return <>{available > 0 ? es.product.available(available) : es.product.noStock}</>;
}

/** Gallery, description, material, season, price and the size and color picker of one product (UC-03). */
function ProductDetailView({ product }: { product: ProductDetail }) {
  const [selection, setSelection] = useState(() => initialSelection(product));
  const hintId = useId();
  const variant = findVariant(product.variants, selection);
  const price = shownPrice(product.variants, selection) ?? { cents: product.fromPriceCents, from: true };
  const canAdd = variant !== undefined && variant.available > 0;
  const viewed = useRef<string | null>(null);

  // RNF-25: one view_item per product page opened.
  useEffect(() => {
    if (viewed.current !== product.slug) {
      viewed.current = product.slug;
      trackItemView(product);
    }
  }, [product]);

  return (
    <div className="pdp-layout">
      <ProductGallery
        key={selection.color ?? ''}
        name={product.name}
        images={galleryFor(product.images, selection.color)}
      />
      <div className="pdp-info">
        <h1>{product.name}</h1>
        <p className="pdp-price">
          {price.from && `${es.product.fromPrice} `}
          {formatMoney(price.cents)}
        </p>
        {product.description !== '' && <p className="pdp-desc">{product.description}</p>}
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
        <VariantPicker
          sizes={product.sizes}
          colors={product.colors}
          variants={product.variants}
          selection={selection}
          onPickSize={(size) => {
            setSelection({ ...selection, size });
          }}
          onPickColor={(color) => {
            setSelection(pickColor(product.variants, selection, color));
          }}
        />
        <p className="stock-note" role="status">
          <StockNote available={variant?.available} />
        </p>
        {/* UC-08 (E2) adds the picked variant to the cart. */}
        <button
          type="button"
          className="btn add-cart"
          disabled={!canAdd}
          {...(variant === undefined ? { 'aria-describedby': hintId } : {})}
        >
          {es.product.addToCart}
        </button>
        {variant === undefined && (
          <p id={hintId} className="pdp-hint">
            {es.product.pickSizeAndColor}
          </p>
        )}
      </div>
    </div>
  );
}

function Skeleton() {
  return (
    <div className="pdp-layout">
      <p className="visually-hidden" role="status">
        {es.product.loading}
      </p>
      <div className="skeleton-ph" aria-hidden="true" />
      <div className="pdp-info" aria-hidden="true">
        <div className="skeleton-text" />
        <div className="skeleton-text short" />
      </div>
    </div>
  );
}

/** The product at /producto/:productSlug; an unknown or unlisted slug shows the 404 page with a link to the shop. */
export function ProductDetailPage() {
  const { productSlug = '' } = useParams();
  const detail = useProductDetail(productSlug);

  if (detail.data?.status === 'notFound') {
    return <NotFoundPage />;
  }

  return (
    <main className="wrap pdp">
      <Link to="/" className="back-link">
        {es.product.backToStore}
      </Link>
      {detail.isPending && <Skeleton />}
      {detail.isError && (
        <div className="empty-state" role="alert">
          <p>{es.product.loadError}</p>
          <button type="button" className="btn" onClick={() => void detail.refetch()}>
            {es.product.retry}
          </button>
        </div>
      )}
      {detail.data?.status === 'found' && (
        <>
          <title>{`${detail.data.product.name} | ${es.store.name}`}</title>
          <ProductDetailView key={detail.data.product.slug} product={detail.data.product} />
        </>
      )}
    </main>
  );
}
