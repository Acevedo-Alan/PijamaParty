import { useState, type CSSProperties } from 'react';
import './BlurredImage.css';

/** A tiny inline thumbnail of the photo (UC-06). Only data: URLs, so the placeholder never loads anything external. */
export type PlaceholderDataUrl = `data:image/${string}`;

interface BlurredImageProps {
  src: string;
  /** Required: RNF-31.4 asks for alternative text on every photo (by default name, color and view). */
  alt: string;
  /** Shown blurred until the photo loads; without it, a gradient of the palette tokens is used. */
  placeholder?: PlaceholderDataUrl;
  /** Responsive files of the photo (`url 400w, ...`) and the width it takes in the layout. */
  srcSet?: string;
  sizes?: string;
  className?: string;
  /** Lazy by default; the main photo of a page loads eagerly because it is the largest paint. */
  loading?: 'lazy' | 'eager';
}

function placeholderStyle(placeholder: PlaceholderDataUrl | undefined): CSSProperties | undefined {
  if (placeholder === undefined || !placeholder.startsWith('data:image/') || /["\\)\s]/.test(placeholder)) {
    return undefined;
  }
  return { '--ph-placeholder': `url("${placeholder}")` } as CSSProperties;
}

/** Photo with the prototype's blurred placeholder (.ph): the placeholder fades out once the image has loaded. */
export function BlurredImage({ src, alt, placeholder, srcSet, sizes, className, loading = 'lazy' }: BlurredImageProps) {
  const [loaded, setLoaded] = useState(false);
  const classes = ['ph', loaded ? 'loaded' : '', className ?? ''].filter(Boolean).join(' ');

  return (
    <div className={classes} style={placeholderStyle(placeholder)} data-testid="blurred-image">
      <img
        src={src}
        srcSet={srcSet}
        sizes={sizes}
        alt={alt}
        loading={loading}
        decoding="async"
        onLoad={() => {
          setLoaded(true);
        }}
      />
    </div>
  );
}
