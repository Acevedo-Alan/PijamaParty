import { describe, expect, it } from 'vitest';
import type { DetailImage, DetailVariant, ProductDetail } from './productDetailApi.ts';
import {
  galleryFor,
  hasStockFor,
  initialSelection,
  isColorSoldOut,
  isSizeAvailable,
  pickColor,
  shownPrice,
} from './variantSelection.ts';

function variant(size: string, color: string, available: number, priceCents = 1_000_000): DetailVariant {
  return { id: `${size}-${color}`, sku: `PP-${size}${color}`, size, color, priceCents, available };
}

function image(src: string, color: string | null): DetailImage {
  return { src, srcSet: `${src} 400w`, color, largeSrc: src };
}

function detail(overrides: Partial<ProductDetail>): ProductDetail {
  return {
    id: 'product-1',
    slug: 'luna',
    name: 'Pijama Luna',
    category: { slug: 'pijamas-largos', name: 'Pijamas largos' },
    description: '',
    material: '',
    season: 'WINTER',
    fromPriceCents: 1_000_000,
    sizes: [],
    colors: [],
    variants: [],
    images: [],
    sizeGuide: null,
    ...overrides,
  };
}

const VARIANTS = [
  variant('S', 'Rosa', 2, 1_000_000),
  variant('M', 'Rosa', 0, 1_100_000),
  variant('M', 'Celeste', 4, 1_200_000),
];

describe('variant selection (UC-03)', () => {
  it('given a picked color, when a size has no stock or no variant in it, then the size is not available', () => {
    expect(isSizeAvailable(VARIANTS, 'S', 'Rosa')).toBe(true);
    expect(isSizeAvailable(VARIANTS, 'M', 'Rosa')).toBe(false);
    expect(isSizeAvailable(VARIANTS, 'S', 'Celeste')).toBe(false);
  });

  it('given no picked color, when some color of the size has stock, then the size is available', () => {
    expect(isSizeAvailable(VARIANTS, 'M', null)).toBe(true);
  });

  it('given a picked size, when the color has no stock in it, then the color is sold out', () => {
    expect(isColorSoldOut(VARIANTS, 'Rosa', 'M')).toBe(true);
    expect(isColorSoldOut(VARIANTS, 'Celeste', 'M')).toBe(false);
    expect(isColorSoldOut(VARIANTS, 'Rosa', null)).toBe(false);
  });

  it('given the picks so far, when no matching variant has stock, then there is nothing to add (T-E7-36)', () => {
    expect(hasStockFor(VARIANTS, { size: null, color: null })).toBe(true);
    expect(hasStockFor(VARIANTS, { size: null, color: 'Celeste' })).toBe(true);
    expect(hasStockFor(VARIANTS, { size: 'M', color: 'Rosa' })).toBe(false);
    expect(hasStockFor([variant('S', 'Rosa', 0)], { size: null, color: 'Rosa' })).toBe(false);
  });

  it('given single-option groups, when the page opens, then they start picked unless the size has no stock', () => {
    expect(initialSelection(detail({ sizes: ['M'], colors: ['Rosa'], variants: [variant('M', 'Rosa', 1)] })))
      .toEqual({ size: 'M', color: 'Rosa' });
    expect(initialSelection(detail({ sizes: ['M'], colors: ['Rosa'], variants: [variant('M', 'Rosa', 0)] })))
      .toEqual({ size: null, color: 'Rosa' });
    expect(initialSelection(detail({ sizes: ['S', 'M'], colors: ['Rosa', 'Celeste'], variants: VARIANTS })))
      .toEqual({ size: null, color: null });
  });

  it('given a picked size, when the color changes, then the size stays only while the combination has stock', () => {
    expect(pickColor(VARIANTS, { size: 'M', color: 'Celeste' }, 'Rosa')).toEqual({ size: null, color: 'Rosa' });
    expect(pickColor(VARIANTS, { size: 'S', color: null }, 'Rosa')).toEqual({ size: 'S', color: 'Rosa' });
  });

  it('given a full pick, when the price is shown, then it is that variant price', () => {
    expect(shownPrice(VARIANTS, { size: 'M', color: 'Celeste' })).toEqual({ cents: 1_200_000, from: false });
  });

  it('given a partial pick, when the matching variants differ in price, then the lowest shows as "from"', () => {
    expect(shownPrice(VARIANTS, { size: null, color: 'Rosa' })).toEqual({ cents: 1_000_000, from: true });
    expect(shownPrice(VARIANTS, { size: null, color: 'Celeste' })).toEqual({ cents: 1_200_000, from: false });
  });

  it('given a picked color, when the gallery is ordered, then its photos come first and the rest keep position', () => {
    const images = [image('a', null), image('b', 'Rosa'), image('c', 'Celeste'), image('d', 'Celeste')];

    expect(galleryFor(images, 'Celeste').map((photo) => photo.src)).toEqual(['c', 'd', 'a', 'b']);
    expect(galleryFor(images, null)).toBe(images);
  });
});
