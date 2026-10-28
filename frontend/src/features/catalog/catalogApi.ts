import { api } from '../../api/client.ts';
import type { components, paths } from '../../api/schema.d.ts';
import type { PlaceholderDataUrl } from '../../components/BlurredImage.tsx';
import { defaultSort, type Audience, type ListingCriteria, type Season } from './listingCriteria.ts';

type ListItemResponse = components['schemas']['ProductListItemResponse'];
/** The placeholder and files of a photo, as the listing and the detail send them. */
type ImageResponse = Pick<components['schemas']['ListingImageResponse'], 'placeholder' | 'files'>;
type ListingQuery = NonNullable<paths['/api/catalog/products']['get']['parameters']['query']>;

export interface ListingImage {
  /** The smallest file, for browsers that ignore srcset. */
  src: string;
  /** The 400, 800 and 1200 px files of the photo (UC-01). */
  srcSet: string;
  placeholder?: PlaceholderDataUrl;
}

export interface ListedProduct {
  slug: string;
  name: string;
  /** The first photo of the product; null when it has none. */
  image: ListingImage | null;
  /** The photo after the first, shown over it on hover (T-E7-14); null without one. */
  secondImage: ListingImage | null;
  fromPriceCents: number;
  soldOut: boolean;
  /** The colors with stock, in display order (D-04). */
  colors: string[];
  /** Every size, in display order, each saying whether one of its colors has stock (T-E7-35). */
  sizes: SizeOffer[];
  /** Created in the last 30 days (D-05). */
  isNew: boolean;
}

export interface SizeOffer {
  size: string;
  inStock: boolean;
}

/**
 * One page of GET /api/catalog/products, with how many products match in all (D-11). An unknown or inactive
 * category is an expected answer, not an error.
 */
export type ListingPage =
  | { status: 'listed'; items: ListedProduct[]; hasMore: boolean; total: number }
  | { status: 'unknownCategory' };

interface ImageFile {
  width: number;
  url: string;
}

function isPlaceholder(value: string | undefined): value is PlaceholderDataUrl {
  return value?.startsWith('data:image/') === true;
}

/** The photo with its srcset, smallest file first; null when it has no complete file. */
export function toImage(image: ImageResponse | null | undefined): ListingImage | null {
  const files = (image?.files ?? [])
    .filter((file): file is ImageFile => file.width !== undefined && file.url !== undefined)
    .toSorted((a, b) => a.width - b.width);
  const smallest = files[0];
  if (smallest === undefined) {
    return null;
  }
  const srcSet = files.map((file) => `${file.url} ${String(file.width)}w`).join(', ');
  const placeholder = image?.placeholder;
  return isPlaceholder(placeholder) ? { src: smallest.url, srcSet, placeholder } : { src: smallest.url, srcSet };
}

function toProduct(item: ListItemResponse): ListedProduct {
  const { slug, name, fromPriceCents } = item;
  if (slug === undefined || name === undefined || fromPriceCents === undefined) {
    throw new Error('Incomplete product in the catalog listing');
  }
  return {
    slug,
    name,
    image: toImage(item.image),
    secondImage: toImage(item.secondImage),
    fromPriceCents,
    soldOut: item.soldOut === true,
    colors: item.colors ?? [],
    sizes: (item.sizes ?? []).flatMap(({ size, inStock }) => (size === undefined ? [] : [{ size, inStock: inStock === true }])),
    isNew: item.isNew === true,
  };
}

/** The API query of a listing or search page: empty filters and the default sort are left out. */
function toQuery(category: string | undefined, criteria: ListingCriteria, page: number): ListingQuery {
  const { filters, sort, text } = criteria;
  return {
    ...(text === undefined ? {} : { q: text }),
    ...(category === undefined ? {} : { category }),
    ...(filters.sizes.length === 0 ? {} : { size: filters.sizes }),
    ...(filters.colors.length === 0 ? {} : { color: filters.colors }),
    ...(filters.materials.length === 0 ? {} : { material: filters.materials }),
    ...(filters.seasons.length === 0 ? {} : { season: filters.seasons }),
    ...(filters.audiences.length === 0 ? {} : { audience: filters.audiences }),
    ...(filters.minPriceCents === undefined ? {} : { minPrice: filters.minPriceCents }),
    ...(filters.maxPriceCents === undefined ? {} : { maxPrice: filters.maxPriceCents }),
    ...(sort === defaultSort(criteria) || sort === 'RELEVANCE' ? {} : { sort }),
    page,
  };
}

/** The listed products of the whole shop, or of one category by slug, filtered and sorted, 24 per page from 0. */
export async function fetchProductPage(
  category: string | undefined,
  criteria: ListingCriteria,
  page: number,
  signal?: AbortSignal,
): Promise<ListingPage> {
  const query = toQuery(category, criteria, page);
  const { data, response } = await api.GET('/api/catalog/products', { params: { query }, signal: signal ?? null });
  if (response.status === 404) {
    return { status: 'unknownCategory' };
  }
  if (data === undefined) {
    throw new Error(`Catalog listing failed with status ${String(response.status)}`);
  }
  const items = (data.items ?? []).map(toProduct);
  return { status: 'listed', items, hasMore: data.hasMore === true, total: data.total ?? items.length };
}

/** What the filters panel offers (UC-02): the values of the listed products, not narrowed by the other filters. */
export interface FilterOptions {
  sizes: string[];
  colors: string[];
  materials: string[];
  seasons: Season[];
  audiences: Audience[];
  /** The lowest and highest "from" price in cents; null without listed products. */
  priceRange: { minCents: number; maxCents: number } | null;
}

export type FilterOptionsAnswer = { status: 'found'; options: FilterOptions } | { status: 'unknownCategory' };

/** The filter options of the whole shop, or of one category by slug. */
export async function fetchFilterOptions(
  category: string | undefined,
  signal?: AbortSignal,
): Promise<FilterOptionsAnswer> {
  const query = category === undefined ? {} : { category };
  const { data, response } = await api.GET('/api/catalog/filters', { params: { query }, signal: signal ?? null });
  if (response.status === 404) {
    return { status: 'unknownCategory' };
  }
  if (data === undefined) {
    throw new Error(`Filter options failed with status ${String(response.status)}`);
  }
  const { minPriceCents, maxPriceCents } = data;
  return {
    status: 'found',
    options: {
      sizes: data.sizes ?? [],
      colors: data.colors ?? [],
      materials: data.materials ?? [],
      seasons: data.seasons ?? [],
      audiences: data.audiences ?? [],
      priceRange:
        typeof minPriceCents === 'number' && typeof maxPriceCents === 'number'
          ? { minCents: minPriceCents, maxCents: maxPriceCents }
          : null,
    },
  };
}
