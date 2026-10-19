import { useState } from 'react';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { es } from '../../i18n/es.ts';
import type { DetailImage } from './productDetailApi.ts';

/** One column on mobile; from 860 px the gallery takes about half of the 1180 px content width. */
const MAIN_IMAGE_SIZES = '(min-width: 1180px) 610px, (min-width: 860px) 52vw, calc(100vw - 40px)';
const THUMB_SIZES = '64px';

interface ProductGalleryProps {
  name: string;
  /** Already ordered: the photos of the picked color come first (UC-03). */
  images: DetailImage[];
}

/**
 * The main photo with the blurred placeholder (blur-up) and a row of thumbnails to switch it. The parent remounts
 * the gallery when the color changes, so it starts again from the first photo of that color.
 */
export function ProductGallery({ name, images }: ProductGalleryProps) {
  const [active, setActive] = useState(0);
  const main = images[active] ?? images[0];

  if (main === undefined) {
    return (
      <div className="pdp-gallery">
        <div className="ph pdp-main" role="img" aria-label={es.product.noPhoto} />
      </div>
    );
  }

  return (
    <section className="pdp-gallery" aria-label={es.product.gallery}>
      <BlurredImage
        // A new photo is a new element, so its placeholder shows again until it loads.
        key={main.src}
        className="pdp-main"
        src={main.src}
        srcSet={main.srcSet}
        sizes={MAIN_IMAGE_SIZES}
        alt={es.product.photoAlt(name, main.color, images.indexOf(main) + 1)}
        loading="eager"
        fetchPriority="high"
        {...(main.placeholder === undefined ? {} : { placeholder: main.placeholder })}
      />
      {images.length > 1 && (
        <ul className="pdp-thumbs">
          {images.map((image, index) => (
            <li key={image.src}>
              <button
                type="button"
                className="pdp-thumb"
                aria-label={es.product.showPhoto(index + 1)}
                aria-pressed={image === main}
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
