import { api } from '../../api/client.ts';

/**
 * The store's contact data, social links and header announcement (UC-36), edited in "Datos de la tienda". A value the
 * store does not have is left out, and so is its link (UC-30, UC-31).
 */
export interface StoreContact {
  email?: string;
  /** In international format, digits only, as wa.me links take it: 549 + area code + number, e.g. 5493424000000. */
  whatsAppNumber?: string;
  /** As it should read, e.g. "Lunes a viernes de 9 a 18 h" (T-E7-26). */
  openingHours?: string;
  /** As it should read and as Google Maps finds it, e.g. "San Martín 2000, Santa Fe" (T-E7-26). */
  address?: string;
  instagramUrl?: string;
  facebookUrl?: string;
  tiktokUrl?: string;
  /** The header strip's text, in place of "Envío gratis desde $X" (UC-36). */
  announcement?: string;
}

const FIELDS = [
  'email',
  'whatsAppNumber',
  'openingHours',
  'address',
  'instagramUrl',
  'facebookUrl',
  'tiktokUrl',
  'announcement',
] as const satisfies readonly (keyof StoreContact)[];

/** GET /api/store/contact (UC-36): the null values are left out. */
export async function fetchStoreContact(signal?: AbortSignal): Promise<StoreContact> {
  const { data, response } = await api.GET('/api/store/contact', { signal: signal ?? null });
  if (data === undefined) {
    throw new Error(`The store contact failed with status ${String(response.status)}`);
  }
  const contact: StoreContact = {};
  for (const field of FIELDS) {
    const value = data[field];
    if (typeof value === 'string' && value !== '') {
      contact[field] = value;
    }
  }
  return contact;
}
