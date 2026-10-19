import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { toImage, type ListingImage } from './catalogApi.ts';

/** The 200 answer; a 204 has no body. */
type BannerResponse = components['schemas']['HomeBannerResponse'];

/** The character banner of the home page as the administrator published it (UC-32). */
export interface HomeBanner {
  title: string;
  body: string;
  linkText: string;
  /** A path of this store, opened with the router (never an external address). */
  linkPath: string;
  /** Null when its files are incomplete: the section then shows the gradient background. */
  image: ListingImage | null;
}

/** One leading slash and no backslash: the server's rule (StorePath), checked again before linking. */
export function isStorePath(path: string): boolean {
  return path.startsWith('/') && !path.startsWith('//') && !path.includes('\\');
}

/** GET /api/home/banner: the published banner, or null when none is published (204). */
export async function fetchHomeBanner(signal?: AbortSignal): Promise<HomeBanner | null> {
  const { data, response } = await api.GET('/api/home/banner', { signal: signal ?? null });
  if (response.status === 204) {
    return null;
  }
  const banner = data as BannerResponse | undefined;
  const { title, body, linkText, linkPath } = banner ?? {};
  if (!response.ok || title === undefined || linkText === undefined || linkPath === undefined) {
    throw new Error(`Home banner failed with status ${String(response.status)}`);
  }
  return { title, body: body ?? '', linkText, linkPath, image: toImage(banner?.image) };
}
