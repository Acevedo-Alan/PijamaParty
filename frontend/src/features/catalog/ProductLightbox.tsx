import { useId, useState, type CSSProperties, type KeyboardEvent, type MouseEvent, type PointerEvent } from 'react';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { es } from '../../i18n/es.ts';
import type { DetailImage } from './productDetailApi.ts';
import { useSwipe } from './useSwipe.ts';

interface ProductLightboxProps {
  open: boolean;
  images: DetailImage[];
  /** The photo shown, shared with the gallery, so the main photo is this one when the view closes. */
  index: number;
  altFor: (index: number) => string;
  onIndexChange: (index: number) => void;
  onClose: () => void;
}

/**
 * The enlarged view of the photos with their 1200 px files (E7): a modal dialog that slides up and closes with
 * Escape, a tap outside or its close button. Its content mounts again on every open, so the zoom starts reset.
 */
export function ProductLightbox({ open, onClose, ...stage }: ProductLightboxProps) {
  const titleId = useId();
  return (
    <PanelDialog open={open} from="bottom" className="product-lightbox" labelledBy={titleId} onDismiss={onClose}>
      <h2 id={titleId} className="visually-hidden">
        {es.product.enlarged}
      </h2>
      <button type="button" className="icon-btn product-lightbox-close" aria-label={es.product.closeEnlarged} onClick={onClose}>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M6 6l12 12M18 6 6 18" />
        </svg>
      </button>
      <LightboxStage {...stage} />
    </PanelDialog>
  );
}

/** Where the zoomed photo is anchored, in percent of its box. */
interface Zoom {
  x: number;
  y: number;
}

function pointIn(event: PointerEvent<HTMLElement> | MouseEvent<HTMLElement>): Zoom {
  const box = event.currentTarget.getBoundingClientRect();
  const clamp = (value: number) => Math.min(100, Math.max(0, value));
  return {
    x: clamp(((event.clientX - box.left) / box.width) * 100),
    y: clamp(((event.clientY - box.top) / box.height) * 100),
  };
}

/**
 * One photo fading into the next (E7 enlarged view): the one left stays below until the new one has faded in.
 * Pressing the photo zooms it to 2x around that point and the zoomed photo follows the pointer or finger.
 */
function LightboxStage({ images, index, altFor, onIndexChange }: Omit<ProductLightboxProps, 'open' | 'onClose'>) {
  const [shown, setShown] = useState(index);
  const [leaving, setLeaving] = useState<number | null>(null);
  const [zoom, setZoom] = useState<Zoom | null>(null);
  const swipe = useSwipe(index, images.length, onIndexChange, zoom === null);

  // A new photo: the one shown until now fades out under it and the zoom resets.
  if (shown !== index) {
    setLeaving(shown);
    setShown(index);
    setZoom(null);
  }

  const image = images[index];
  if (image === undefined) {
    return null;
  }
  const previous = leaving === null ? undefined : images[leaving];

  function go(next: number) {
    if (next >= 0 && next < images.length) {
      onIndexChange(next);
    }
  }

  function step(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
      event.preventDefault();
      go(index + (event.key === 'ArrowRight' ? 1 : -1));
    }
  }

  const photoStyle = (
    zoom === null ? undefined : { transform: 'scale(2)', transformOrigin: `${String(zoom.x)}% ${String(zoom.y)}%` }
  ) satisfies CSSProperties | undefined;

  return (
    <div className="product-lightbox-body" onKeyDown={step}>
      <div
        className={`product-lightbox-stage${zoom === null ? '' : ' zoomed'}`}
        {...swipe.handlers}
        onPointerMove={(event) => {
          if (zoom === null) {
            swipe.handlers.onPointerMove(event);
          } else {
            setZoom(pointIn(event));
          }
        }}
        onClick={(event) => {
          setZoom(zoom === null ? pointIn(event) : null);
        }}
      >
        {previous !== undefined && <img className="product-lightbox-leaving" src={previous.largeSrc} alt="" aria-hidden="true" />}
        <img
          key={image.largeSrc}
          className="product-lightbox-photo"
          src={image.largeSrc}
          alt={altFor(index)}
          style={photoStyle}
          draggable={false}
          onAnimationEnd={() => {
            setLeaving(null);
          }}
        />
      </div>
      {images.length > 1 && (
        <>
          <button
            type="button"
            className="icon-btn product-lightbox-nav prev"
            aria-label={es.product.previousPhoto}
            disabled={index === 0}
            onClick={() => {
              go(index - 1);
            }}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m15 5-7 7 7 7" />
            </svg>
          </button>
          <button
            type="button"
            className="icon-btn product-lightbox-nav next"
            aria-label={es.product.nextPhoto}
            disabled={index === images.length - 1}
            onClick={() => {
              go(index + 1);
            }}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m9 5 7 7-7 7" />
            </svg>
          </button>
          <p className="product-lightbox-count" aria-live="polite">
            {es.product.photoCount(index + 1, images.length)}
          </p>
        </>
      )}
    </div>
  );
}
