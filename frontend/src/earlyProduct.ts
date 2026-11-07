/**
 * T-E6-02 (RNF-11): its own tiny entry of index.html, run while the app's bundle is still downloading. On a product
 * page it asks for the product right away and, as soon as the answer arrives, asks the browser to load the gallery's
 * first photo, which is the page's largest paint: the photo no longer waits for the app to download, run and draw
 * the gallery. The app takes the answer in `fetchProductDetail`; without it, the app asks itself.
 * It imports nothing, so it stays one small file that never waits for another.
 */
export interface EarlyProduct {
  slug: string;
  response: Promise<Response>;
}

declare global {
  interface Window {
    earlyProduct?: EarlyProduct | undefined;
  }
}

/** The `sizes` of the gallery's main photo, the same as ProductGallery.tsx (a test keeps them equal). */
export const MAIN_PHOTO_SIZES = '(min-width: 1180px) 534px, (min-width: 860px) calc(52vw - 76px), calc(100vw - 40px)';

interface PhotoFile {
  width?: number;
  url?: string;
}

interface PhotoAnswer {
  colors?: string[];
  images?: { color?: string; files?: PhotoFile[] }[];
}

/**
 * The files of the photo the gallery shows first, as an `srcset` (smallest first, as the page writes it): the first
 * photo of the only color when the product has one, since the page picks it, or else the first photo.
 */
export function firstPhotoSrcSet(product: PhotoAnswer): { src: string; srcSet: string } | undefined {
  const only = product.colors?.length === 1 ? product.colors[0] : undefined;
  const images = product.images ?? [];
  const image = images.find((candidate) => candidate.color === only) ?? images[0];
  const files = (image?.files ?? [])
    .filter((file): file is { width: number; url: string } => file.width !== undefined && file.url !== undefined)
    .toSorted((a, b) => a.width - b.width);
  const smallest = files[0];
  return smallest === undefined
    ? undefined
    : { src: smallest.url, srcSet: files.map((file) => `${file.url} ${String(file.width)}w`).join(', ') };
}

function preloadFirstPhoto(response: Response): void {
  // Cloned here, before the app reads the same answer.
  response
    .clone()
    .json()
    .then((product: PhotoAnswer) => {
      const photo = firstPhotoSrcSet(product);
      if (photo !== undefined) {
        const link = document.createElement('link');
        link.rel = 'preload';
        link.as = 'image';
        link.href = photo.src;
        link.setAttribute('imagesrcset', photo.srcSet);
        link.setAttribute('imagesizes', MAIN_PHOTO_SIZES);
        link.setAttribute('fetchpriority', 'high');
        document.head.append(link);
      }
    })
    .catch(() => undefined);
}

const match = /^\/producto\/([^/]+)\/?$/.exec(window.location.pathname);
if (match?.[1] !== undefined) {
  try {
    const slug = decodeURIComponent(match[1]);
    const response = fetch(`/api/catalog/products/${encodeURIComponent(slug)}`, { credentials: 'same-origin' });
    window.earlyProduct = { slug, response };
    response.then((answer) => {
      if (answer.ok) {
        preloadFirstPhoto(answer);
      }
    }, () => undefined);
  } catch {
    // A malformed address: the app answers it as it always did.
    window.earlyProduct = undefined;
  }
}
