import { api } from '../../api/client.ts';
import { toSizeGuide, type SizeGuide } from './productDetailApi.ts';

/** An active category and its guide, on the size guide page (T-E7-29). */
export interface CategorySizeGuide {
  category: { slug: string; name: string };
  guide: SizeGuide;
}

/** GET /api/catalog/size-guides: the guide of every active category that has one, by the categories' position. */
export async function fetchSizeGuides(signal?: AbortSignal): Promise<CategorySizeGuide[]> {
  const { data, response } = await api.GET('/api/catalog/size-guides', { signal: signal ?? null });
  if (data === undefined) {
    throw new Error(`Size guides failed with status ${String(response.status)}`);
  }
  return (data.items ?? []).map(({ category, ...guide }) => {
    if (category?.slug === undefined || category.name === undefined) {
      throw new Error('Incomplete category of a size guide');
    }
    return { category: { slug: category.slug, name: category.name }, guide: toSizeGuide(guide) };
  });
}
