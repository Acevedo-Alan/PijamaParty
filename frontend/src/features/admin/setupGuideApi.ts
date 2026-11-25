import { api } from '../../api/client.ts';
import { ensureSignedIn } from './adminSession.ts';

/** UC-46: the store setup guide of the summary; its own read, so it loads and fails on its own. */
export const SETUP_GUIDE_KEY = ['admin', 'setup-guide'] as const;

/** The items in the order the summary shows them. */
export const SETUP_ITEMS = ['WHATSAPP', 'EMAIL', 'SIZE_GUIDES', 'PRODUCT_PHOTOS', 'INSTAGRAM', 'HOME_BANNER'] as const;

export type SetupItem = (typeof SETUP_ITEMS)[number];

export type SetupState = 'DONE' | 'PENDING' | 'OMITTED';

/** Only what a store may do without can be omitted (UC-46); the server refuses the others with 409. */
export const OMITTABLE: ReadonlySet<SetupItem> = new Set<SetupItem>(['INSTAGRAM', 'HOME_BANNER']);

export interface SetupEntry {
  item: SetupItem;
  state: SetupState;
  /** How many categories or products the item is about while pending; 0 for the other items. */
  total: number;
  /** The first of them, at most 5. */
  names: { id: string; name: string }[];
}

function isItem(value: unknown): value is SetupItem {
  return SETUP_ITEMS.some((item) => item === value);
}

export async function fetchSetupGuide(signal?: AbortSignal): Promise<SetupEntry[]> {
  const { data, response } = await api.GET('/api/admin/setup-guide', { signal: signal ?? null });
  ensureSignedIn(response);
  if (data === undefined) {
    throw new Error(`Setup guide failed with status ${String(response.status)}`);
  }
  // An item this page does not know yet (a newer server) is left out rather than shown without its texts.
  return (data.items ?? []).flatMap((entry) =>
    isItem(entry.item)
      ? [{
          item: entry.item,
          state: entry.state ?? 'PENDING',
          total: entry.total ?? 0,
          names: (entry.names ?? []).map((name) => ({ id: name.id ?? '', name: name.name ?? '' })),
        }]
      : [],
  );
}

/** "Omitir": idempotent on the server. */
export async function omitSetupItem(item: SetupItem): Promise<void> {
  const { response } = await api.PUT('/api/admin/setup-guide/omitted/{item}', { params: { path: { item } } });
  ensureSignedIn(response);
  if (response.status !== 204) {
    throw new Error(`Omitting ${item} failed with status ${String(response.status)}`);
  }
}

/** "Volver a mostrar": idempotent on the server. */
export async function showSetupItem(item: SetupItem): Promise<void> {
  const { response } = await api.DELETE('/api/admin/setup-guide/omitted/{item}', { params: { path: { item } } });
  ensureSignedIn(response);
  if (response.status !== 204) {
    throw new Error(`Showing ${item} again failed with status ${String(response.status)}`);
  }
}
