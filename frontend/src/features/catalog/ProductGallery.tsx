import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { es } from '../../i18n/es.ts';
import type { DetailImage } from './productDetailApi.ts';
import { ProductLightbox } from './ProductLightbox.tsx';
import { useSwipe } from './useSwipe.ts';

/**
 * One column on mobile; from 860 px the gallery takes about half of the 1180 px content width, less the strip of
 * thumbnails on its left.
 */
const MAIN_IMAGE_SIZES = '(min-width: 1180px) 534px, (min-width: 860px) calc(52vw - 76px), calc(100vw - 40px)';
const THUMB_SIZES = '64px';

interface ProductGalleryProps {
  name: string;
  /** Already ordered: the photos of the picked color come first (UC-03). */
  images: DetailImage[];
}

/** Scrolls the strip of thumbnails just enough to show the picked one, never the page. */
function keepInSight(list: HTMLElement, item: HTMLElement) {
  if (item.offsetLeft < list.scrollLeft) {
    list.scrollLeft = item.offsetLeft;
  } else if (item.offsetLeft + item.offsetWidth > list.scrollLeft + list.clientWidth) {
    list.scrollLeft = item.offsetLeft + item.offsetWidth - list.clientWidth;
  }
  if (item.offsetTop < list.scrollTop) {
    list.scrollTop = item.offsetTop;
  } else if (item.offsetTop + item.offsetHeight > list.scrollTop + list.clientHeight) {
    list.scrollTop = item.offsetTop + item.offsetHeight - list.clientHeight;
  }
}

/**
 * The photos side by side, sliding to the picked one by thumbnail, swipe or arrow keys (E7 gallery motion), each with
 * the blurred placeholder (blur-up); pressed to enlarge it. The thumbnails are a row below it on phones, a strip on
 * its left from 860 px. The parent remounts the gallery when the color changes, so it starts again from the first
 * photo of that color.
 */
export function ProductGallery({ name, images }: ProductGalleryProps) {
  const [active, setActive] = useState(0);
  const [enlarged, setEnlarged] = useState(false);
  const thumbs = useRef<HTMLUListElement>(null);
  const swipe = useSwipe(active, images.length, setActive);

  useEffect(() => {
    const list = thumbs.current;
    const item = list?.children[active];
    if (list !== null && item instanceof HTMLElement) {
      keepInSight(list, item);
    }
  }, [active]);

  if (images.length === 0) {
    return (
      <div className="pdp-gallery">
        <div className="ph pdp-main" role="img" aria-label={es.product.noPhoto} />
      </div>
    );
  }

  const altFor = (index: number) => es.product.photoAlt(name, images[index]?.color ?? null, index + 1);

  function step(event: KeyboardEvent<HTMLElement>) {
    const next = event.key === 'ArrowRight' ? active + 1 : event.key === 'ArrowLeft' ? active - 1 : null;
    if (next !== null && next >= 0 && next < images.length) {
      event.preventDefault();
      setActive(next);
    }
  }

  const trackStyle = {
    transform: `translateX(calc(${String(-active * 100)}% + ${String(swipe.offset ?? 0)}px))`,
  } satisfies CSSProperties;

  return (
    <section className="pdp-gallery" aria-label={es.product.gallery} onKeyDown={step}>
      <div className="pdp-image-frame" {...swipe.handlers}>
        <div className={`pdp-track${swipe.offset === null ? '' : ' dragging'}`} style={trackStyle}>
          {images.map((image, index) => (
            <div key={image.src} className="pdp-slide" aria-hidden={index !== active}>
              <BlurredImage
                className="pdp-main"
                src={image.src}
                srcSet={image.srcSet}
                sizes={MAIN_IMAGE_SIZES}
                alt={altFor(index)}
                // The shown photo and its neighbours, so a swipe never reveals an empty placeholder.
                loading={Math.abs(index - active) <= 1 ? 'eager' : 'lazy'}
                fetchPriority={index === 0 ? 'high' : 'auto'}
                {...(image.placeholder === undefined ? {} : { placeholder: image.placeholder })}
              />
            </div>
          ))}
        </div>
        {/* The whole photo opens the enlarged view; the icon in its corner says so. */}
        <button
          type="button"
          className="pdp-zoom"
          aria-label={es.product.enlargePhoto}
          aria-haspopup="dialog"
          onClick={() => {
            setEnlarged(true);
          }}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="10.8" cy="10.8" r="6.5" />
            <path d="m16 16 4.5 4.5M10.8 7.5v6.6M7.5 10.8h6.6" />
          </svg>
        </button>
      </div>
      <ProductLightbox
        open={enlarged}
        images={images}
        index={active}
        altFor={altFor}
        onIndexChange={setActive}
        onClose={() => {
          setEnlarged(false);
        }}
      />
      {images.length > 1 && (
        <ul className="pdp-thumbs" ref={thumbs}>
          {images.map((image, index) => (
            <li key={image.src}>
              <button
                type="button"
                className="pdp-thumb"
                aria-label={es.product.showPhoto(index + 1)}
                aria-pressed={index === active}
                onClick={() => {
                  setActive(index);
                }}
              >
                <BlurredImage
                  src={image.src}
                  srcSet={image.srcSet}
                  sizes={THUMB_SIZES}
                  alt=""
                  {...(image.placeholder === undefined ? {} : { placeholder: image.placeholder })}
                />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
