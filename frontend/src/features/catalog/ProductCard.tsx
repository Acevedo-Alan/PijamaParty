import { useState, type CSSProperties, type PointerEvent } from 'react';
import { Link } from 'react-router';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { ListedProduct } from './catalogApi.ts';
import { CardSizes } from './CardSizes.tsx';
import { ColorSwatches } from './ColorSwatches.tsx';
import type { ListingView } from './useListingView.ts';
import { useSwipe } from './useSwipe.ts';

/** Two columns on mobile, three from 640 px and four from 980 px, inside the 1180 px content width. */
const GRID_IMAGE_SIZES = '(min-width: 980px) 280px, (min-width: 640px) 33vw, 50vw';
/** The list view's thumbnail (T-E7-06). */
const LIST_IMAGE_SIZES = '96px';

/** How a card's photo loads: the first ones of a listing are the largest paint of the page (T-E6-02, RNF-11). */
export type CardPhotoPriority = 'lazy' | 'eager' | 'high';

/**
 * A product of the grid (UC-01): first photo, the second one on hover in the grid view (T-E7-14) or by a swipe on
 * touch devices (T-E7-32), the "Nuevo" (D-05)
 * and "Sold out" labels, the colors with stock (D-04), the sizes (T-E7-35), name and "from" price.
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
  const { slug, name, image, secondImage, fromPriceCents, soldOut, colors, sizes, isNew } = product;
  const swipeable = image !== null && secondImage !== null && view === 'grid';
  const [shown, setShown] = useState(0);
  // The second photo is requested on touch devices only once a finger touches this card's photo (T-E7-32).
  const [touched, setTouched] = useState(false);
  const swipe = useSwipe(shown, swipeable ? 2 : 1, setShown);
  const mediaStyle = swipeable
    ? ({ '--photo': shown, '--drag': `${String(swipe.offset ?? 0)}px` } as CSSProperties)
    : undefined;

  return (
    <Link to={`/producto/${encodeURIComponent(slug)}`} className="card">
      <div
        className={`card-media${swipeable ? ' swipeable' : ''}${swipe.offset === null ? '' : ' dragging'}`}
        style={mediaStyle}
        {...(swipeable && {
          ...swipe.handlers,
          // Only a finger swipes: the mouse shows the second photo on hover instead (T-E7-14).
          onPointerDown: (event: PointerEvent<HTMLDivElement>) => {
            if (event.pointerType === 'touch') {
              setTouched(true);
              swipe.handlers.onPointerDown(event);
            }
          },
        })}
      >
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
        {/* Hidden by CSS except on hover devices, or on touch devices once touched, so a phone downloads it only when
            it may be swiped to; the first photo carries the name, so this one is decorative. */}
        {swipeable && (
          <img
            className={`card-second-photo${touched ? ' touched' : ''}`}
            src={secondImage.src}
            srcSet={secondImage.srcSet}
            sizes={GRID_IMAGE_SIZES}
            alt=""
            loading="lazy"
            decoding="async"
            data-testid="second-photo"
          />
        )}
        {swipeable && (
          <span className="card-dots" aria-hidden="true" data-testid="photo-dots">
            <span className={shown === 0 ? 'on' : undefined} />
            <span className={shown === 1 ? 'on' : undefined} />
          </span>
        )}
        {(isNew || soldOut) && (
          <div className="card-badges">
            {isNew && <span className="badge new">{es.catalog.isNew}</span>}
            {soldOut && <span className="badge sold-out">{es.catalog.soldOut}</span>}
          </div>
        )}
      </div>
      <ColorSwatches colors={colors} />
      <CardSizes sizes={sizes} />
      <h2 className="card-title">{name}</h2>
      <p className="price">
        {es.catalog.fromPrice} {formatMoney(fromPriceCents)}
      </p>
    </Link>
  );
}
