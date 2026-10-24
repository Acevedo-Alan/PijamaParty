import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn } from './adminSession.ts';

type EntryResponse = components['schemas']['ActivityEntryResponse'];

export type ActivityArea = NonNullable<EntryResponse['area']>;
export type ActivityAction = NonNullable<EntryResponse['action']>;
export type SubjectType = NonNullable<NonNullable<EntryResponse['subject']>['type']>;

/** The order of the area filter (UC-37). */
export const ACTIVITY_AREAS: readonly ActivityArea[] = [
  'CATALOG',
  'STOCK',
  'ORDERS',
  'PROMOTIONS',
  'REVIEWS',
  'SHIPPING',
  'CONTENT',
  'SETTINGS',
  'CUSTOMERS',
  'ACCOUNT',
];

/** One changed field, before and after as display text; null when it had or has no value. */
export interface ActivityChange {
  field: string;
  before: string | null;
  after: string | null;
}

/** One change made from the back office (UC-37). */
export interface ActivityEntry {
  id: string;
  occurredAt: Date;
  area: ActivityArea;
  action: ActivityAction;
  subject: { type: SubjectType; id: string | null; label: string | null };
  changes: ActivityChange[];
}

/** The filters of the "Actividad" screen; dates as yyyy-mm-dd, store dates, both inclusive. */
export interface ActivityFilter {
  area: ActivityArea | null;
  from: string | null;
  to: string | null;
}

function toEntry(row: EntryResponse): ActivityEntry {
  const { id, occurredAt, area, action, subject } = row;
  if (
    id === undefined ||
    occurredAt === undefined ||
    area === undefined ||
    action === undefined ||
    subject?.type === undefined
  ) {
    throw new Error('Incomplete activity entry');
  }
  return {
    id,
    occurredAt: new Date(occurredAt),
    area,
    action,
    subject: { type: subject.type, id: subject.id ?? null, label: subject.label ?? null },
    changes: (row.changes ?? []).map((change) => ({
      field: change.field ?? '',
      before: change.before ?? null,
      after: change.after ?? null,
    })),
  };
}

/** One page of entries, newest first, within the filter. */
export async function fetchActivity(
  filter: ActivityFilter,
  page: number,
  signal?: AbortSignal,
): Promise<{ items: ActivityEntry[]; hasMore: boolean }> {
  const { data, response } = await api.GET('/api/admin/activity', {
    params: {
      query: {
        page,
        ...(filter.area === null ? {} : { area: filter.area }),
        ...(filter.from === null ? {} : { from: filter.from }),
        ...(filter.to === null ? {} : { to: filter.to }),
      },
    },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (data === undefined) {
    throw new Error(`Activity list failed with status ${String(response.status)}`);
  }
  return { items: (data.items ?? []).map(toEntry), hasMore: data.hasMore === true };
}
