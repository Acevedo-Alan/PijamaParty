import { Link } from 'react-router';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { ListedProduct } from './catalogApi.ts';
import { ColorSwatches } from './ColorSwatches.tsx';
import type { ListingView } from './useListingView.ts';

/** Two columns on mobile, three from 640 px and four from 980 px, inside the 1180 px content width. */
const GRID_IMAGE_SIZES = '(min-width: 980px) 280px, (min-width: 640px) 33vw, 50vw';
/** The list view's thumbnail (T-E7-06). */
const LIST_IMAGE_SIZES = '96px';

/** How a card's photo loads: the first ones of a listing are the largest paint of the page (T-E6-02, RNF-11). */
export type CardPhotoPriority = 'lazy' | 'eager' | 'high';

/**
 * A product of the grid (UC-01): first photo, the second one on hover in the grid view (T-E7-14), the "Nuevo" (D-05)
 * and "Sold out" labels, the colors with stock (D-04), name and "from" price.
 */
export function ProductCard({
  product,
  photo = 'lazy',
  view = 'grid',
}: {
  product: ListedProduct;
  photo?: CardPhotoPriority;
  view?: ListingView;
}) {
  const { slug, name, image, secondImage, fromPriceCents, soldOut, colors, isNew } = product;

  return (
    <Link to={`/producto/${encodeURIComponent(slug)}`} className="card">
      <div className="card-media">
        {image === null ? (
          <div className="ph" data-testid="no-photo" />
        ) : (
          <BlurredImage
            src={image.src}
            srcSet={image.srcSet}
            sizes={view === 'list' ? LIST_IMAGE_SIZES : GRID_IMAGE_SIZES}
            alt={name}
            loading={photo === 'lazy' ? 'lazy' : 'eager'}
            fetchPriority={photo === 'high' ? 'high' : 'auto'}
            {...(image.placeholder === undefined ? {} : { placeholder: image.placeholder })}
          />
        )}
        {/* Hidden by CSS except on hover devices, so touch devices never download it; the first photo carries the
            name, so this one is decorative. */}
        {image !== null && secondImage !== null && view === 'grid' && (
          <img
            className="card-second-photo"
            src={secondImage.src}
            srcSet={secondImage.srcSet}
            sizes={GRID_IMAGE_SIZES}
            alt=""
            loading="lazy"
            decoding="async"
            data-testid="second-photo"
          />
        )}
        {(isNew || soldOut) && (
          <div className="card-badges">
            {isNew && <span className="badge new">{es.catalog.isNew}</span>}
            {soldOut && <span className="badge sold-out">{es.catalog.soldOut}</span>}
          </div>
        )}
      </div>
      <ColorSwatches colors={colors} />
      <h2 className="card-title">{name}</h2>
      <p className="price">
        {es.catalog.fromPrice} {formatMoney(fromPriceCents)}
      </p>
    </Link>
  );
}
