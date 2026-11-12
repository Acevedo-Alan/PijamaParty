import { memo, useState, type CSSProperties, type PointerEvent } from 'react';
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
 * touch devices (T-E7-32), the "Nuevo" (D-05) and "Sold out" labels, the colors with stock (D-04) at the left of
 * the sizes (T-E7-35), the name and the price, "from" only when its variants have more than one. T-E7-70: in the list
 * view the labels leave the small photo for small words beside the colors, the price sits at the photo's bottom
 * edge, and a light chevron ends the row.
 */
export const ProductCard = memo(function ProductCard({
  product,
  photo = 'lazy',
  view = 'grid',
  headingLevel = 2,
}: {
  product: ListedProduct;
  photo?: CardPhotoPriority;
  view?: ListingView;
  /** One level below the title of the section the card sits in (a card in "Completá tu look" is an h3). */
  headingLevel?: 2 | 3;
}) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3';
  const { slug, name, image, secondImage, fromPriceCents, priceVaries, soldOut, colors, sizes, isNew } = product;
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
            alt=""
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
        {view === 'grid' && (isNew || soldOut) && (
          <div className="card-badges">
            {isNew && <span className="badge new">{es.catalog.isNew}</span>}
            {soldOut && <span className="badge sold-out">{es.catalog.soldOut}</span>}
          </div>
        )}
      </div>
      {(colors.length > 0 || sizes.length > 0 || (view === 'list' && (isNew || soldOut))) && (
        <div className="card-meta">
          {view === 'list' ? (
            // T-E7-70: on a 96 px thumbnail the label would hide the print, so it is a word beside the colors.
            <div className="card-meta-start">
              <ColorSwatches colors={colors} />
              {isNew && <span className="card-tag new">{es.catalog.isNew}</span>}
              {soldOut && <span className="card-tag sold-out">{es.catalog.soldOut}</span>}
            </div>
          ) : (
            <ColorSwatches colors={colors} />
          )}
          <CardSizes sizes={sizes} />
        </div>
      )}
      <Heading className="card-title">{name}</Heading>
      {/* UC-01: "Desde" only when its variants have more than one price. */}
      <p className="price">
        {priceVaries && <span className="price-from">{es.catalog.fromPrice}&nbsp;</span>}
        {formatMoney(fromPriceCents)}
      </p>
      {view === 'list' && (
        // T-E7-70: anchors the row's bottom edge; the whole card is the link, so it is decorative.
        <span className="card-more" aria-hidden="true">
          <svg viewBox="0 0 24 24">
            <path d="M9.5 6.5 15 12l-5.5 5.5" />
          </svg>
        </span>
      )}
    </Link>
  );
});
