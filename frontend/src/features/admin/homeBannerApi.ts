import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { toImage, type ListingImage } from '../catalog/catalogApi.ts';
import { ensureSignedIn, problemProperty } from './adminSession.ts';
import { uploadTransport } from './uploadTransport.ts';

type BannerResponse = components['schemas']['AdminHomeBannerResponse'];

/** What the administrator types (UC-32); the server trims and checks it. */
export interface BannerTexts {
  title: string;
  body: string;
  linkText: string;
  linkPath: string;
}

/** The stored banner: its texts are null until first saved, its photo until uploaded. */
export interface AdminBanner {
  texts: BannerTexts | null;
  image: ListingImage | null;
  published: boolean;
}

/** The fields of INVALID_BANNER and the parts of INCOMPLETE_BANNER, in the API's own names. */
export const BANNER_FIELDS = ['title', 'body', 'linkText', 'linkPath'] as const;
export type BannerField = (typeof BANNER_FIELDS)[number];
export const BANNER_PARTS = ['photo', 'title', 'linkText', 'linkPath'] as const;
export type BannerPart = (typeof BANNER_PARTS)[number];

export type PhotoProblem = 'UNSUPPORTED_FORMAT' | 'TOO_LARGE' | 'UNREADABLE' | 'KEY_REUSED';
const PHOTO_PROBLEMS: readonly PhotoProblem[] = ['UNSUPPORTED_FORMAT', 'TOO_LARGE', 'UNREADABLE', 'KEY_REUSED'];

export type SaveTextsAnswer = { status: 'saved'; banner: AdminBanner } | { status: 'invalid'; fields: BannerField[] };

export type PublishAnswer = { status: 'published'; banner: AdminBanner } | { status: 'incomplete'; missing: BannerPart[] };

export type PhotoAnswer =
  | { status: 'uploaded'; banner: AdminBanner }
  | { status: 'fileTooLarge' }
  | { status: 'invalid'; problem: PhotoProblem }
  /** 503: other photos are being converted; the same key and bytes can be sent again. */
  | { status: 'busy' };

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

function toBanner(data: BannerResponse | undefined, action: string, response: Response): AdminBanner {
  if (data?.published === undefined) {
    throw failed(action, response);
  }
  const { title, body, linkText, linkPath } = data;
  // The texts are saved together and none of title, link text and target can be empty.
  const texts = title && linkText && linkPath ? { title, body: body ?? '', linkText, linkPath } : null;
  return { texts, image: toImage(data.image), published: data.published };
}

/** Only the names the API documents, in its order. */
function namesOf<T extends string>(known: readonly T[], value: unknown): T[] {
  return Array.isArray(value) ? known.filter((name) => value.includes(name)) : [];
}

export async function fetchBanner(signal?: AbortSignal): Promise<AdminBanner> {
  const { data, response } = await api.GET('/api/admin/home/banner', { signal: signal ?? null });
  ensureSignedIn(response);
  return toBanner(data, 'Home banner', response);
}

/** Replaces the four texts; a published banner stays published. */
export async function saveBannerTexts(texts: BannerTexts): Promise<SaveTextsAnswer> {
  const { data, error, response } = await api.PUT('/api/admin/home/banner', { body: texts });
  ensureSignedIn(response);
  if (response.status === 422) {
    return { status: 'invalid', fields: namesOf(BANNER_FIELDS, problemProperty(error, 'fields')) };
  }
  return { status: 'saved', banner: toBanner(data, 'Home banner save', response) };
}

export async function publishBanner(): Promise<PublishAnswer> {
  const { data, error, response } = await api.POST('/api/admin/home/banner/publish');
  ensureSignedIn(response);
  if (response.status === 409) {
    return { status: 'incomplete', missing: namesOf(BANNER_PARTS, problemProperty(error, 'missing')) };
  }
  return { status: 'published', banner: toBanner(data, 'Home banner publication', response) };
}

export async function hideBanner(): Promise<AdminBanner> {
  const { data, response } = await api.POST('/api/admin/home/banner/hide');
  ensureSignedIn(response);
  return toBanner(data, 'Home banner hiding', response);
}

/** Uploads the photo that replaces the current one; the same key and bytes can be sent again without a duplicate. */
export async function uploadBannerPhoto(
  file: Blob,
  fileName: string,
  idempotencyKey: string,
  onProgress?: (sent: number) => void,
): Promise<PhotoAnswer> {
  const { status, body } = await uploadTransport.send({
    url: '/api/admin/home/banner/photo',
    file,
    fileName,
    idempotencyKey,
    ...(onProgress === undefined ? {} : { onProgress }),
  });
  const response = new Response(null, { status });
  ensureSignedIn(response);
  switch (status) {
    case 413:
      return { status: 'fileTooLarge' };
    case 422: {
      const problem = problemProperty(body, 'problem');
      const known = PHOTO_PROBLEMS.find((name) => name === problem);
      if (known === undefined) {
        throw failed('Banner photo upload', response);
      }
      return { status: 'invalid', problem: known };
    }
    case 503:
      return { status: 'busy' };
    default:
      if (!response.ok) {
        throw failed('Banner photo upload', response);
      }
      return { status: 'uploaded', banner: toBanner(body as BannerResponse, 'Banner photo upload', response) };
  }
}
