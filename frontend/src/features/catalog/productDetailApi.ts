import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { toImage, type ListingImage } from './catalogApi.ts';
import type { Season } from './listingCriteria.ts';

type DetailResponse = components['schemas']['ProductDetailResponse'];
type VariantResponse = components['schemas']['VariantDetailResponse'];
type SizeGuideResponse = components['schemas']['SizeGuideResponse'];

/** An active priced variant with its own price and exact available stock (UC-03). */
export interface DetailVariant {
  id: string;
  /** The item id of the analytics events (T-E2-12). */
  sku: string;
  size: string;
  color: string;
  priceCents: number;
  available: number;
}

/** The size guide of the product's category (RNF-31.5): one value per column in each row; blank values show "–". */
export interface SizeGuide {
  columns: string[];
  rows: { size: string; values: string[] }[];
  note: string | null;
}

/** A photo of the gallery: of one color, or of the whole product when the color is null. */
export interface DetailImage extends ListingImage {
  color: string | null;
}

export interface ProductDetail {
  /** What the customer's review of the product refers to (UC-24). */
  id: string;
  slug: string;
  name: string;
  description: string;
  material: string;
  season: Season;
  fromPriceCents: number;
  /** The sizes and colors of the shown variants, in display order. */
  sizes: string[];
  colors: string[];
  variants: DetailVariant[];
  /** The photos of the whole product and of the shown colors, by position. */
  images: DetailImage[];
  /** Null when the category has no guide, so the page shows no link. */
  sizeGuide: SizeGuide | null;
}

/** GET /api/catalog/products/{slug}. A product that is not listed is an expected answer, not an error. */
export type ProductDetailAnswer = { status: 'found'; product: ProductDetail } | { status: 'notFound' };

function toVariant({ id, sku, size, color, priceCents, available }: VariantResponse): DetailVariant {
  if (id === undefined || sku === undefined || size === undefined || color === undefined || priceCents === undefined) {
    throw new Error('Incomplete variant in the product detail');
  }
  return { id, sku, size, color, priceCents, available: available ?? 0 };
}

function toSizeGuide({ columns, rows, note }: SizeGuideResponse): SizeGuide {
  if (columns === undefined || rows === undefined) {
    throw new Error('Incomplete size guide');
  }
  return {
    columns,
    rows: rows.map(({ size, values }) => {
      if (size === undefined || values === undefined) {
        throw new Error('Incomplete size guide row');
      }
      return { size, values };
    }),
    note: note ?? null,
  };
}

function toDetail(data: DetailResponse): ProductDetail {
  const { id, slug, name, season, fromPriceCents } = data;
  if (id === undefined || slug === undefined || name === undefined || season === undefined || fromPriceCents === undefined) {
    throw new Error('Incomplete product detail');
  }
  const images = (data.images ?? []).flatMap((image) => {
    const photo = toImage(image);
    return photo === null ? [] : [{ ...photo, color: image.color ?? null }];
  });
  return {
    id,
    slug,
    name,
    description: data.description ?? '',
    material: data.material ?? '',
    season,
    fromPriceCents,
    sizes: data.sizes ?? [],
    colors: data.colors ?? [],
    variants: (data.variants ?? []).map(toVariant),
    images,
    sizeGuide: data.sizeGuide ? toSizeGuide(data.sizeGuide) : null,
  };
}

/** A listed product by slug. A slug the API rejects as too long finds nothing either, so it is not found too. */
export async function fetchProductDetail(slug: string, signal?: AbortSignal): Promise<ProductDetailAnswer> {
  const { data, response } = await api.GET('/api/catalog/products/{slug}', {
    params: { path: { slug } },
    signal: signal ?? null,
  });
  if (response.status === 404 || response.status === 400) {
    return { status: 'notFound' };
  }
  if (data === undefined) {
    throw new Error(`Product detail failed with status ${String(response.status)}`);
  }
  return { status: 'found', product: toDetail(data) };
}
