import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { toImage, type ListingImage } from './catalogApi.ts';
import type { Season } from './listingCriteria.ts';

type DetailResponse = components['schemas']['ProductDetailResponse'];
type VariantResponse = components['schemas']['VariantDetailResponse'];

/** An active priced variant with its own price and exact available stock (UC-03). */
export interface DetailVariant {
  id: string;
  size: string;
  color: string;
  priceCents: number;
  available: number;
}

/** A photo of the gallery: of one color, or of the whole product when the color is null. */
export interface DetailImage extends ListingImage {
  color: string | null;
}

export interface ProductDetail {
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
}

/** GET /api/catalog/products/{slug}. A product that is not listed is an expected answer, not an error. */
export type ProductDetailAnswer = { status: 'found'; product: ProductDetail } | { status: 'notFound' };

function toVariant({ id, size, color, priceCents, available }: VariantResponse): DetailVariant {
  if (id === undefined || size === undefined || color === undefined || priceCents === undefined) {
    throw new Error('Incomplete variant in the product detail');
  }
  return { id, size, color, priceCents, available: available ?? 0 };
}

function toDetail(data: DetailResponse): ProductDetail {
  const { slug, name, season, fromPriceCents } = data;
  if (slug === undefined || name === undefined || season === undefined || fromPriceCents === undefined) {
    throw new Error('Incomplete product detail');
  }
  const images = (data.images ?? []).flatMap((image) => {
    const photo = toImage(image);
    return photo === null ? [] : [{ ...photo, color: image.color ?? null }];
  });
  return {
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
