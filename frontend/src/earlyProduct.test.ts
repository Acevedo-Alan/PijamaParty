import { describe, expect, it } from 'vitest';
import gallerySource from './features/catalog/ProductGallery.tsx?raw';
import { firstPhotoSrcSet, MAIN_PHOTO_SIZES } from './earlyProduct.ts';

/** T-E6-02: the photo that index.html preloads has to be the one the gallery shows first, in the same size. */
describe('the early photo preload', () => {
  it('asks for the sizes the gallery gives its main photo', () => {
    expect(gallerySource).toContain(`'${MAIN_PHOTO_SIZES}'`);
  });

  it('writes the files smallest first, as the page does', () => {
    const files = [
      { width: 800, url: '/uploads/a-800.webp' },
      { width: 400, url: '/uploads/a-400.webp' },
    ];
    expect(firstPhotoSrcSet({ images: [{ files }] })).toEqual({
      src: '/uploads/a-400.webp',
      srcSet: '/uploads/a-400.webp 400w, /uploads/a-800.webp 800w',
    });
  });

  it('picks the first photo of the only color, the one the page shows first', () => {
    const product = {
      colors: ['Rosa'],
      images: [
        { color: 'Azul', files: [{ width: 400, url: '/uploads/azul.webp' }] },
        { color: 'Rosa', files: [{ width: 400, url: '/uploads/rosa.webp' }] },
      ],
    };
    expect(firstPhotoSrcSet(product)?.src).toBe('/uploads/rosa.webp');
  });

  it('picks the first photo when the product has several colors or the only color has none', () => {
    const images = [
      { color: 'Azul', files: [{ width: 400, url: '/uploads/azul.webp' }] },
      { color: 'Rosa', files: [{ width: 400, url: '/uploads/rosa.webp' }] },
    ];
    expect(firstPhotoSrcSet({ colors: ['Azul', 'Rosa'], images })?.src).toBe('/uploads/azul.webp');
    expect(firstPhotoSrcSet({ colors: ['Verde'], images })?.src).toBe('/uploads/azul.webp');
  });

  it('preloads nothing when there is no usable photo', () => {
    expect(firstPhotoSrcSet({})).toBeUndefined();
    expect(firstPhotoSrcSet({ images: [{ files: [{ url: '/uploads/a.webp' }] }] })).toBeUndefined();
  });
});
