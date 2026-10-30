import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { trackAddToCart } from '../analytics/cartEvents.ts';
import { trackItemView } from '../analytics/catalogEvents.ts';
import type { AddAnswer } from '../cart/cartApi.ts';
import { useAddToCart } from '../cart/useCart.ts';
import { NotFoundPage } from '../errors/NotFoundPage.tsx';
import { PurchaseInfoSummary } from '../help/PurchaseInfoSummary.tsx';
import { MyReviewSection } from '../reviews/MyReviewSection.tsx';
import { ProductReviewsSection, RatingSummary } from '../reviews/ProductReviews.tsx';
import { REVIEWS_SHOWN } from '../reviews/reviewsShown.ts';
import { PageMeta } from '../seo/PageMeta.tsx';
import { productDescription } from '../seo/productMeta.ts';
import { ProductStructuredData } from '../seo/ProductStructuredData.tsx';
import { AddButtonLabel, addButtonState } from './AddButtonLabel.tsx';
import { ProductBreadcrumbs } from './ProductBreadcrumbs.tsx';
import { ProductGallery } from './ProductGallery.tsx';
import type { ProductDetail } from './productDetailApi.ts';
import { ProductSections } from './ProductSections.tsx';
import { QuantityInput } from './QuantityInput.tsx';
import { RecentlyViewed } from './RecentlyViewed.tsx';
import { rememberViewed } from './recentlyViewed.ts';
import { RelatedProducts } from './RelatedProducts.tsx';
import { ShippingByProvince } from './ShippingByProvince.tsx';
import { SizeGuideLink } from './SizeGuideSection.tsx';
import { StickyAddToCart } from './StickyAddToCart.tsx';
import { productDetailKey, useProductDetail } from './useProductDetail.ts';
import { VariantPicker } from './VariantPicker.tsx';
import {
  findVariant,
  galleryFor,
  hasStockFor,
  initialSelection,
  pickColor,
  shownPrice,
  type Selection,
} from './variantSelection.ts';
import './catalog.css';
import './productDetail.css';

function StockNote({ available, soldOutColor }: { available: number | undefined; soldOutColor: boolean }) {
  // T-E7-36: why the add button is disabled before a size is picked.
  if (soldOutColor) {
    return <>{es.product.noStockInColor}</>;
  }
  if (available === undefined) {
    return null;
  }
  return <>{available > 0 ? es.product.available(available) : es.product.noStock}</>;
}

/** Why the last add failed (UC-08); nothing after a successful add, which the cart panel confirms. */
function AddProblem({ answer, failed }: { answer: AddAnswer | undefined; failed: boolean }) {
  if (failed) {
    return <>{es.product.addError}</>;
  }
  if (answer?.status === 'insufficientStock') {
    return <>{es.product.onlyLeft(answer.available)}</>;
  }
  if (answer?.status === 'unavailable') {
    return <>{es.product.noLongerAvailable}</>;
  }
  return null;
}

/** How long the add button says "Agregado" after a successful add (E7 motion). */
const ADDED_FOR_MS = 1500;

function Price({ price }: { price: { cents: number; from: boolean } }) {
  return (
    <p className="pdp-price">
      {price.from && `${es.product.fromPrice} `}
      {formatMoney(price.cents)}
    </p>
  );
}

/**
 * Gallery, description, price, the size and color picker, the quantity and the add to cart of one product, then the
 * collapsed sections, the shipping by province and the trust icons (E7); "Completá tu look" below both columns.
 */
function ProductDetailView({ product }: { product: ProductDetail }) {
  const [selection, setSelection] = useState(() => initialSelection(product));
  const [quantity, setQuantity] = useState(1);
  // T-E7-36: the add button was pressed while the size or the color was still not picked.
  const [showMissing, setShowMissing] = useState(false);
  const variant = findVariant(product.variants, selection);
  const price = shownPrice(product.variants, selection) ?? { cents: product.fromPriceCents, from: true };
  const missingPick = selection.size === null || selection.color === null;
  const canAdd = variant !== undefined && variant.available > 0;
  // A reload after a rejected add can lower the stock below the chosen quantity.
  const units = Math.max(1, Math.min(quantity, variant?.available ?? 1));
  const viewed = useRef<string | null>(null);
  const addButton = useRef<HTMLButtonElement>(null);
  const picker = useRef<HTMLDivElement>(null);
  const add = useAddToCart();
  const queryClient = useQueryClient();
  // Successful adds on this page: each one shows "Agregado" again for ADDED_FOR_MS, then 0 hides it.
  const [confirmed, setConfirmed] = useState(0);

  useEffect(() => {
    if (confirmed === 0) {
      return;
    }
    const timer = setTimeout(() => {
      setConfirmed(0);
    }, ADDED_FOR_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [confirmed]);

  /** T-E7-36: the first group still without a pick (the size first) comes into view and takes the focus. */
  function focusMissingPick() {
    const group = picker.current?.querySelector(`[data-group="${selection.size === null ? 'size' : 'color'}"]`);
    group?.scrollIntoView({ block: 'center' });
    group?.querySelector<HTMLInputElement>('input:not(:disabled)')?.focus({ preventScroll: true });
  }

  // UC-08 with the chosen quantity (E7). A rejected add reloads the product, so its stock is current.
  function addPicked() {
    if (missingPick) {
      // Rendered before the focus moves, so the group's description is read with it.
      flushSync(() => {
        setShowMissing(true);
      });
      focusMissingPick();
      return;
    }
    if (variant === undefined) {
      return;
    }
    const quantity = units;
    add.mutate({ variantId: variant.id, quantity }, {
      onSuccess: (answer) => {
        if (answer.status !== 'added') {
          void queryClient.invalidateQueries({ queryKey: productDetailKey(product.slug) });
          return;
        }
        setConfirmed((count) => count + 1);
        // RNF-25: one add_to_cart per successful add, at the server's price.
        const added = answer.cart.items.find((item) => item.variantId === variant.id);
        trackAddToCart({
          sku: variant.sku,
          name: product.name,
          size: variant.size,
          color: variant.color,
          unitPriceCents: added?.unitPriceCents ?? variant.priceCents,
          quantity,
        });
      },
    });
  }

  // RNF-25: one view_item per product page opened.
  useEffect(() => {
    if (viewed.current !== product.slug) {
      viewed.current = product.slug;
      trackItemView(product);
    }
  }, [product]);

  function pick(next: Selection) {
    setSelection(next);
    setQuantity(1);
    add.reset();
  }

  // The main button and the one of the fixed bar on phones do the same. Without a pick it stays enabled, to say what
  // is missing (T-E7-36), unless nothing that matches the picks has stock, as with a sold-out color.
  const addState = addButtonState(add, confirmed > 0);
  const addButtonProps = {
    type: 'button',
    className: 'btn add-cart btn-progress',
    'data-state': addState,
    disabled: add.isPending || (missingPick ? !hasStockFor(product.variants, selection) : !canAdd),
    onClick: addPicked,
  } as const;
  const addLabel = <AddButtonLabel state={addState} />;
  // E7: the fixed bar says what its button adds, as the picker is off screen by then.
  const picked = es.product.pickedSummary(selection.size, selection.color);
  const pickedId = useId();

  return (
    <div className="pdp-layout">
      <ProductGallery
        key={selection.color ?? ''}
        name={product.name}
        images={galleryFor(product.images, selection.color)}
      />
      <div className="pdp-info">
        <h1>{product.name}</h1>
        {REVIEWS_SHOWN && <RatingSummary slug={product.slug} />}
        <Price price={price} />
        {product.description !== '' && <p className="pdp-desc">{product.description}</p>}
        <VariantPicker
          ref={picker}
          sizes={product.sizes}
          colors={product.colors}
          variants={product.variants}
          selection={selection}
          showMissing={showMissing}
          sizeGuide={product.sizeGuide !== null && <SizeGuideLink guide={product.sizeGuide} />}
          onPickSize={(size) => {
            pick({ ...selection, size });
          }}
          onPickColor={(color) => {
            pick(pickColor(product.variants, selection, color));
          }}
        />
        <p className="stock-note" role="status">
          <StockNote
            available={variant?.available}
            soldOutColor={missingPick && selection.color !== null && !hasStockFor(product.variants, selection)}
          />
        </p>
        <div className="pdp-purchase-row">
          <QuantityInput value={units} max={variant?.available ?? 0} onChange={setQuantity} />
          <button ref={addButton} {...addButtonProps}>
            {addLabel}
          </button>
        </div>
        <p className="pdp-add-error" role="alert">
          <AddProblem answer={add.data} failed={add.isError} />
        </p>
        <ProductSections product={product} />
        <ShippingByProvince />
        <PurchaseInfoSummary />
      </div>
      <StickyAddToCart target={addButton}>
        <div className="pdp-sticky-summary">
          <Price price={price} />
          {picked !== null && (
            <p id={pickedId} className="pdp-sticky-picked">
              {picked}
            </p>
          )}
        </div>
        {/* Its name stays the main button's; the picks describe it, as the picker is off screen. */}
        <button {...addButtonProps} aria-describedby={picked === null ? undefined : pickedId}>
          {addLabel}
        </button>
      </StickyAddToCart>
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
  const shownSlug = detail.data?.status === 'found' ? detail.data.product.slug : null;

  // T-E7-33: a product that loads joins the browser's "Vistos recientemente".
  useEffect(() => {
    if (shownSlug !== null) {
      rememberViewed(shownSlug);
    }
  }, [shownSlug]);

  if (detail.data?.status === 'notFound') {
    return <NotFoundPage />;
  }

  return (
    <main className="wrap pdp">
      {detail.data?.status === 'found' && <ProductBreadcrumbs product={detail.data.product} />}
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
          <PageMeta description={productDescription(detail.data.product)} />
          <ProductStructuredData product={detail.data.product} />
          <ProductDetailView key={detail.data.product.slug} product={detail.data.product} />
          {/* Below both columns, so the info column is short enough to stay on screen (UC-03) */}
          <RelatedProducts category={detail.data.product.category.slug} slug={detail.data.product.slug} />
          {/* Its own key, not the slug alone: the product view beside it already uses that one. */}
          <RecentlyViewed key={`recent-${detail.data.product.slug}`} exclude={detail.data.product.slug} />
          {REVIEWS_SHOWN && (
            <>
              <ProductReviewsSection key={detail.data.product.slug} slug={detail.data.product.slug} />
              <MyReviewSection key={detail.data.product.id} productId={detail.data.product.id} />
            </>
          )}
        </>
      )}
    </main>
  );
}
