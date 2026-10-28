import { useState } from 'react';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { es } from '../../i18n/es.ts';
import type { DetailImage } from './productDetailApi.ts';
import { ProductLightbox } from './ProductLightbox.tsx';

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

/**
 * The main photo with the blurred placeholder (blur-up), pressed to enlarge it (E7), and the thumbnails to switch it:
 * a row below it on phones, a strip on its left from 860 px. The parent remounts
 * the gallery when the color changes, so it starts again from the first photo of that color.
 */
export function ProductGallery({ name, images }: ProductGalleryProps) {
  const [active, setActive] = useState(0);
  const [enlarged, setEnlarged] = useState(false);
  const main = images[active] ?? images[0];

  if (main === undefined) {
    return (
      <div className="pdp-gallery">
        <div className="ph pdp-main" role="img" aria-label={es.product.noPhoto} />
      </div>
    );
  }

  const alt = es.product.photoAlt(name, main.color, images.indexOf(main) + 1);

  return (
    <section className="pdp-gallery" aria-label={es.product.gallery}>
      <div className="pdp-image-frame">
        <BlurredImage
          // A new photo is a new element, so its placeholder shows again until it loads.
          key={main.src}
          className="pdp-main"
          src={main.src}
          srcSet={main.srcSet}
          sizes={MAIN_IMAGE_SIZES}
          alt={alt}
          loading="eager"
          fetchPriority="high"
          {...(main.placeholder === undefined ? {} : { placeholder: main.placeholder })}
        />
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
        image={main}
        alt={alt}
        onClose={() => {
          setEnlarged(false);
        }}
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
