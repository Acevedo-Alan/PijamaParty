import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn, problemProperty } from './adminSession.ts';

type SettingsResponse = components['schemas']['StoreSettingsResponse'];

/** The texts of "Datos de la tienda" (UC-36) as the administrator types them; empty is none. */
export const SETTINGS_TEXTS = [
  'email',
  'whatsAppNumber',
  'openingHours',
  'address',
  'instagramUrl',
  'facebookUrl',
  'tiktokUrl',
  'announcement',
] as const;
export type SettingsText = (typeof SETTINGS_TEXTS)[number];

/** The fields INVALID_SETTINGS names, in the API's own names. */
export const SETTINGS_FIELDS = [...SETTINGS_TEXTS, 'lowStockThreshold'] as const;
export type SettingsField = (typeof SETTINGS_FIELDS)[number];

/** The stored settings: every text empty when the store has none. */
export type StoreSettings = Record<SettingsText, string> & { lowStockThreshold: number; version: number };

/** What a save sends: the texts, the threshold as typed, and the version the form was opened with. */
export type SettingsDraft = Record<SettingsText, string> & { lowStockThreshold: string; version: number };

export type SaveAnswer =
  | { status: 'saved'; settings: StoreSettings }
  | { status: 'invalid'; fields: SettingsField[] }
  /** 409 SETTINGS_CHANGED: another save got in first; nothing was saved. */
  | { status: 'changed' };

function toSettings(data: SettingsResponse | undefined, action: string, response: Response): StoreSettings {
  if (data?.version === undefined || data.lowStockThreshold === undefined) {
    throw new Error(`${action} failed with status ${String(response.status)}`);
  }
  const texts = Object.fromEntries(SETTINGS_TEXTS.map((field) => [field, data[field] ?? ''])) as Record<
    SettingsText,
    string
  >;
  return { ...texts, lowStockThreshold: data.lowStockThreshold, version: data.version };
}

export async function fetchStoreSettings(signal?: AbortSignal): Promise<StoreSettings> {
  const { data, response } = await api.GET('/api/admin/store/settings', { signal: signal ?? null });
  ensureSignedIn(response);
  return toSettings(data, 'Store settings', response);
}

/**
 * Saves every value at once; the server checks the rules. A threshold that is not a whole number is refused here, as
 * the server would, without sending anything.
 */
export async function saveStoreSettings(draft: SettingsDraft): Promise<SaveAnswer> {
  const typed = draft.lowStockThreshold.trim();
  if (!/^-?\d{1,9}$/.test(typed)) {
    return { status: 'invalid', fields: ['lowStockThreshold'] };
  }
  const texts = Object.fromEntries(SETTINGS_TEXTS.map((field) => [field, draft[field].trim() || null]));
  const { data, error, response } = await api.PUT('/api/admin/store/settings', {
    body: { ...texts, lowStockThreshold: Number(typed), version: draft.version },
  });
  ensureSignedIn(response);
  if (response.status === 422) {
    const fields = problemProperty(error, 'fields');
    return {
      status: 'invalid',
      fields: Array.isArray(fields) ? SETTINGS_FIELDS.filter((field) => fields.includes(field)) : [],
    };
  }
  if (response.status === 409) {
    return { status: 'changed' };
  }
  return { status: 'saved', settings: toSettings(data, 'Store settings save', response) };
}
