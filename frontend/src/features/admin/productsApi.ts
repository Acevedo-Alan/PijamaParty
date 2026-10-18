import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { toImage, type ListingImage } from '../catalog/catalogApi.ts';
import type { Audience, Season } from '../catalog/listingCriteria.ts';
import { ensureSignedIn, problemProperty, problemText } from './adminSession.ts';
import { uploadTransport } from './uploadTransport.ts';

type ProductResponse = components['schemas']['ProductResponse'];
type SummaryResponse = components['schemas']['ProductSummaryResponse'];
type VariantResponse = components['schemas']['VariantResponse'];
type ImageResponse = components['schemas']['ImageResponse'];

export interface ProductSummary {
  id: string;
  categoryId: string;
  name: string;
  published: boolean;
  variantCount: number;
}

/** What the back office types about a product (UC-06); the server trims and checks it. */
export interface ProductDetails {
  categoryId: string;
  name: string;
  description: string;
  material: string;
  season: Season;
  audience: Audience;
}

export type DetailsField = 'CATEGORY' | 'NAME' | 'DESCRIPTION' | 'MATERIAL' | 'SEASON' | 'AUDIENCE';

export interface AdminVariant {
  id: string;
  sku: string;
  size: string;
  color: string;
  /** Null until the administrator sets a price. */
  priceCents: number | null;
  active: boolean;
}

export interface AdminImage {
  id: string;
  /** Null for a photo of the whole product. */
  color: string | null;
  image: ListingImage;
}

export interface AdminProduct extends ProductDetails {
  id: string;
  slug: string;
  published: boolean;
  /** Sizes and colors in display order. */
  sizes: string[];
  colors: string[];
  variants: AdminVariant[];
  /** The photos by position. */
  images: AdminImage[];
}

export type ProductAnswer = { status: 'found'; product: AdminProduct } | { status: 'notFound' };

export type SaveDetailsAnswer =
  | { status: 'saved'; product: AdminProduct }
  | { status: 'slugAlreadyUsed'; slug: string }
  | { status: 'invalid'; fields: DetailsField[] }
  | { status: 'notFound' };

export type MatrixProblem =
  | 'INVALID_SIZE'
  | 'INVALID_COLOR'
  | 'NO_SIZES'
  | 'NO_COLORS'
  | 'REPEATED_SIZES'
  | 'REPEATED_COLORS'
  | 'TOO_MANY_SIZES'
  | 'TOO_MANY_COLORS';

export type GenerateAnswer =
  | { status: 'generated'; product: AdminProduct; created: number }
  | { status: 'invalid'; problem: MatrixProblem }
  | { status: 'notFound' };

export type PublishAnswer =
  | { status: 'saved'; product: AdminProduct }
  | { status: 'needsPricedVariant' }
  | { status: 'notFound' };

export type VariantAnswer =
  | { status: 'saved'; product: AdminProduct }
  | { status: 'lastActivePricedVariant' }
  | { status: 'invalidPrice' }
  | { status: 'notFound' };

export type ImageProblem = 'UNSUPPORTED_FORMAT' | 'TOO_LARGE' | 'UNREADABLE' | 'UNKNOWN_COLOR' | 'KEY_REUSED';

export type UploadAnswer =
  | { status: 'uploaded' }
  | { status: 'tooManyImages' }
  | { status: 'fileTooLarge' }
  | { status: 'invalid'; problem: ImageProblem }
  | { status: 'notFound' }
  /** 503: two conversions are running; the same key and bytes can be sent again. */
  | { status: 'busy' };

const DETAILS_FIELDS: readonly DetailsField[] = ['CATEGORY', 'NAME', 'DESCRIPTION', 'MATERIAL', 'SEASON', 'AUDIENCE'];
const MATRIX_PROBLEMS: readonly MatrixProblem[] = [
  'INVALID_SIZE',
  'INVALID_COLOR',
  'NO_SIZES',
  'NO_COLORS',
  'REPEATED_SIZES',
  'REPEATED_COLORS',
  'TOO_MANY_SIZES',
  'TOO_MANY_COLORS',
];
const IMAGE_PROBLEMS: readonly ImageProblem[] = [
  'UNSUPPORTED_FORMAT',
  'TOO_LARGE',
  'UNREADABLE',
  'UNKNOWN_COLOR',
  'KEY_REUSED',
];

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

function oneOf<T extends string>(values: readonly T[], value: unknown): value is T {
  return values.includes(value as T);
}

function toSummary({ id, categoryId, name, published, variantCount }: SummaryResponse): ProductSummary {
  if (id === undefined || categoryId === undefined || name === undefined) {
    throw new Error('Incomplete product summary');
  }
  return { id, categoryId, name, published: published === true, variantCount: variantCount ?? 0 };
}

function toVariant({ id, sku, size, color, priceCents, active }: VariantResponse): AdminVariant {
  if (id === undefined || sku === undefined || size === undefined || color === undefined) {
    throw new Error('Incomplete variant');
  }
  return { id, sku, size, color, priceCents: priceCents ?? null, active: active === true };
}

function toAdminImage({ id, color, ...files }: ImageResponse): AdminImage[] {
  const image = toImage(files);
  return id === undefined || image === null ? [] : [{ id, color: color ?? null, image }];
}

export function toProduct(data: ProductResponse): AdminProduct {
  const { id, categoryId, name, slug, season, audience } = data;
  if (
    id === undefined ||
    categoryId === undefined ||
    name === undefined ||
    slug === undefined ||
    season === undefined ||
    audience === undefined
  ) {
    throw new Error('Incomplete product');
  }
  return {
    id,
    categoryId,
    name,
    slug,
    description: data.description ?? '',
    material: data.material ?? '',
    season,
    audience,
    published: data.published === true,
    sizes: data.sizes ?? [],
    colors: data.colors ?? [],
    variants: (data.variants ?? []).map(toVariant),
    images: (data.images ?? []).flatMap(toAdminImage),
  };
}

/** One page of every product, published or not, newest first, 50 per page from 0. */
export async function fetchProductSummaries(
  page: number,
  signal?: AbortSignal,
): Promise<{ items: ProductSummary[]; hasMore: boolean }> {
  const { data, response } = await api.GET('/api/admin/catalog/products', {
    params: { query: { page } },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (data === undefined) {
    throw failed('Product list', response);
  }
  return { items: (data.items ?? []).map(toSummary), hasMore: data.hasMore === true };
}

/** Bounds the pages read by {@link fetchAllProductSummaries}: 5,000 products, far above the shop's catalog. */
const MAX_SUMMARY_PAGES = 100;

/** Every product, published or not, newest first, reading the pages one after the other (UC-23 scope pickers). */
export async function fetchAllProductSummaries(signal?: AbortSignal): Promise<ProductSummary[]> {
  const products: ProductSummary[] = [];
  for (let page = 0; page < MAX_SUMMARY_PAGES; page++) {
    const { items, hasMore } = await fetchProductSummaries(page, signal);
    products.push(...items);
    if (!hasMore) {
      break;
    }
  }
  return products;
}

export async function fetchAdminProduct(id: string, signal?: AbortSignal): Promise<ProductAnswer> {
  const { data, response } = await api.GET('/api/admin/catalog/products/{id}', {
    params: { path: { id } },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (data === undefined) {
    throw failed('Product', response);
  }
  return { status: 'found', product: toProduct(data) };
}

function detailsAnswer(data: ProductResponse | undefined, error: unknown, response: Response): SaveDetailsAnswer {
  ensureSignedIn(response);
  switch (response.status) {
    case 404:
      return { status: 'notFound' };
    case 409:
      return { status: 'slugAlreadyUsed', slug: problemText(error, 'slug') };
    case 422: {
      // Without fields the category does not exist.
      const fields = problemProperty(error, 'fields');
      const known = Array.isArray(fields) ? fields.filter((field) => oneOf(DETAILS_FIELDS, field)) : [];
      return { status: 'invalid', fields: known.length === 0 ? ['CATEGORY'] : known };
    }
    default:
      if (data === undefined) {
        throw failed('Product save', response);
      }
      return { status: 'saved', product: toProduct(data) };
  }
}

/** A new product is hidden and has no variants (UC-06). */
export async function createProduct(details: ProductDetails): Promise<SaveDetailsAnswer> {
  const { data, error, response } = await api.POST('/api/admin/catalog/products', { body: details });
  return detailsAnswer(data, error, response);
}

export async function updateProduct(id: string, details: ProductDetails): Promise<SaveDetailsAnswer> {
  const { data, error, response } = await api.PUT('/api/admin/catalog/products/{id}', {
    params: { path: { id } },
    body: details,
  });
  return detailsAnswer(data, error, response);
}

/** Adds the missing size × color combinations; never deletes a variant. */
export async function generateVariants(
  product: AdminProduct,
  sizes: string[],
  colors: string[],
): Promise<GenerateAnswer> {
  const { data, error, response } = await api.POST('/api/admin/catalog/products/{id}/variants/generate', {
    params: { path: { id: product.id } },
    body: { sizes, colors },
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (response.status === 422) {
    const problem = problemProperty(error, 'problem');
    if (!oneOf(MATRIX_PROBLEMS, problem)) {
      throw failed('Variant generation', response);
    }
    return { status: 'invalid', problem };
  }
  if (data === undefined) {
    throw failed('Variant generation', response);
  }
  const generated = toProduct(data);
  return { status: 'generated', product: generated, created: generated.variants.length - product.variants.length };
}

export async function setPublished(id: string, published: boolean): Promise<PublishAnswer> {
  const options = { params: { path: { id } } };
  const { data, response } = published
    ? await api.POST('/api/admin/catalog/products/{id}/publish', options)
    : await api.POST('/api/admin/catalog/products/{id}/hide', options);
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (response.status === 409) {
    return { status: 'needsPricedVariant' };
  }
  if (data === undefined) {
    throw failed('Product state change', response);
  }
  return { status: 'saved', product: toProduct(data) };
}

/** Sets the price (in cents) or the active flag of a variant; the server answers the whole product. */
export async function updateVariant(
  id: string,
  change: { priceCents: number } | { active: boolean },
): Promise<VariantAnswer> {
  const { data, response } = await api.PATCH('/api/admin/catalog/variants/{id}', {
    params: { path: { id } },
    body: change,
  });
  ensureSignedIn(response);
  switch (response.status) {
    case 404:
      return { status: 'notFound' };
    case 409:
      return { status: 'lastActivePricedVariant' };
    case 422:
      return { status: 'invalidPrice' };
    default:
      if (data === undefined) {
        throw failed('Variant update', response);
      }
      return { status: 'saved', product: toProduct(data) };
  }
}

/**
 * Uploads one photo. The same key and file after a lost answer returns the stored photo instead of a second one
 * (UC-06). A lost connection rejects, so the caller can offer to retry with the same key.
 */
export async function uploadImage(
  productId: string,
  file: Blob,
  fileName: string,
  color: string | null,
  idempotencyKey: string,
  onProgress?: (sent: number) => void,
): Promise<UploadAnswer> {
  const query = color === null ? '' : `?${new URLSearchParams({ color }).toString()}`;
  const { status, body } = await uploadTransport.send({
    url: `/api/admin/catalog/products/${encodeURIComponent(productId)}/images${query}`,
    file,
    fileName,
    idempotencyKey,
    ...(onProgress === undefined ? {} : { onProgress }),
  });
  const response = new Response(null, { status });
  ensureSignedIn(response);
  switch (status) {
    case 404:
      return { status: 'notFound' };
    case 409:
      return { status: 'tooManyImages' };
    case 413:
      return { status: 'fileTooLarge' };
    case 422: {
      const problem = problemProperty(body, 'problem');
      if (!oneOf(IMAGE_PROBLEMS, problem)) {
        throw failed('Photo upload', response);
      }
      return { status: 'invalid', problem };
    }
    case 503:
      return { status: 'busy' };
    default:
      if (!response.ok) {
        throw failed('Photo upload', response);
      }
      return { status: 'uploaded' };
  }
}

/** Sends every photo id in the new order; null when the product no longer exists. */
export async function reorderImages(productId: string, imageIds: string[]): Promise<AdminProduct | null> {
  const { data, response } = await api.PUT('/api/admin/catalog/products/{id}/images/order', {
    params: { path: { id: productId } },
    body: { imageIds },
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return null;
  }
  if (data === undefined) {
    throw failed('Photo reorder', response);
  }
  return toProduct(data);
}

/** Deletes a photo; false when it was already gone. */
export async function deleteImage(productId: string, imageId: string): Promise<boolean> {
  const { response } = await api.DELETE('/api/admin/catalog/products/{id}/images/{imageId}', {
    params: { path: { id: productId, imageId } },
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return false;
  }
  if (!response.ok) {
    throw failed('Photo deletion', response);
  }
  return true;
}
