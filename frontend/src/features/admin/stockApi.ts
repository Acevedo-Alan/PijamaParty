import { api } from '../../api/client.ts';
import type { components, paths } from '../../api/schema.d.ts';
import { ensureSignedIn, problemProperty } from './adminSession.ts';

type MovementResponse = components['schemas']['MovementResponse'];
export type MovementType = NonNullable<MovementResponse['type']>;

export interface StockMovement {
  id: string;
  type: MovementType;
  /** Signed: positive adds, negative subtracts. */
  quantity: number;
  note: string | null;
  /** Null for a user the administrator cannot see (UC-07). */
  userName: string | null;
  createdAt: Date;
}

export type MovementPage =
  | { status: 'listed'; onHand: number; items: StockMovement[]; hasMore: boolean }
  | { status: 'unknownVariant' };

export type AdjustAnswer =
  | { status: 'adjusted'; onHand: number }
  | { status: 'outOfRange'; problem: 'WOULD_GO_NEGATIVE' | 'WOULD_EXCEED_MAXIMUM'; onHand: number }
  /** UC-16: orders awaiting payment hold `reserved` units, which an adjustment cannot take away. */
  | { status: 'belowReserved'; onHand: number; reserved: number }
  | { status: 'invalid'; problem: 'INVALID_QUANTITY' | 'INVALID_NOTE' | 'KEY_REUSED' }
  | { status: 'unknownVariant' };

function toMovement({ id, type, quantity, note, userName, createdAt }: MovementResponse): StockMovement {
  if (id === undefined || type === undefined || quantity === undefined || createdAt === undefined) {
    throw new Error('Incomplete stock movement');
  }
  return { id, type, quantity, note: note ?? null, userName: userName ?? null, createdAt: new Date(createdAt) };
}

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

/** The current stock of a variant and one page of its movements, newest first, 50 per page from 0. */
export async function fetchMovements(variantId: string, page: number, signal?: AbortSignal): Promise<MovementPage> {
  const { data, response } = await api.GET('/api/admin/variants/{id}/stock-movements', {
    params: { path: { id: variantId }, query: { page } },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'unknownVariant' };
  }
  if (data === undefined) {
    throw failed('Stock history', response);
  }
  return {
    status: 'listed',
    onHand: data.onHand ?? 0,
    items: (data.items ?? []).map(toMovement),
    hasMore: data.hasMore === true,
  };
}

/**
 * Adds or subtracts stock (UC-07). The same key, quantity and note after a lost answer changes nothing and returns
 * the current stock. A lost connection rejects, so the caller can retry with the same key.
 */
export async function adjustStock(
  variantId: string,
  quantity: number,
  note: string,
  idempotencyKey: string,
): Promise<AdjustAnswer> {
  const { data, error, response } = await api.POST('/api/admin/stock-adjustments', {
    params: { header: { 'Idempotency-Key': idempotencyKey } },
    body: note === '' ? { variantId, quantity } : { variantId, quantity, note },
  });
  ensureSignedIn(response);
  const problem = problemProperty(error, 'problem');
  const onHand = problemProperty(error, 'onHand');
  if (response.status === 404) {
    return { status: 'unknownVariant' };
  }
  if (
    response.status === 409 &&
    (problem === 'WOULD_GO_NEGATIVE' || problem === 'WOULD_EXCEED_MAXIMUM') &&
    typeof onHand === 'number'
  ) {
    return { status: 'outOfRange', problem, onHand };
  }
  const reserved = problemProperty(error, 'reserved');
  if (
    response.status === 409 &&
    problem === 'WOULD_GO_BELOW_RESERVED' &&
    typeof onHand === 'number' &&
    typeof reserved === 'number'
  ) {
    return { status: 'belowReserved', onHand, reserved };
  }
  if (
    response.status === 422 &&
    (problem === 'INVALID_QUANTITY' || problem === 'INVALID_NOTE' || problem === 'KEY_REUSED')
  ) {
    return { status: 'invalid', problem };
  }
  if (data?.onHand === undefined) {
    throw failed('Stock adjustment', response);
  }
  return { status: 'adjusted', onHand: data.onHand };
}

type StockRowResponse = components['schemas']['StockRowResponse'];
type StockListQuery = NonNullable<paths['/api/admin/catalog/stock']['get']['parameters']['query']>;
export type StockFilter = NonNullable<StockListQuery['filter']>;

/** UC-07 (T-E8-09): the stock levels the Stock screen filters by, besides every variant. */
export const STOCK_FILTERS = ['LOW', 'OUT'] as const satisfies readonly StockFilter[];

/** One row of the Stock screen: an active variant and its units (available is on hand minus reserved). */
export interface StockRow {
  variantId: string;
  productId: string;
  productName: string;
  size: string;
  color: string;
  sku: string;
  published: boolean;
  onHand: number;
  reserved: number;
  available: number;
}

export interface StockListFilter {
  filter: StockFilter;
  q: string | null;
  categoryId: string | null;
}

function toStockRow(row: StockRowResponse): StockRow {
  const { variantId, productId, productName, size, color, sku } = row;
  if (
    variantId === undefined ||
    productId === undefined ||
    productName === undefined ||
    size === undefined ||
    color === undefined ||
    sku === undefined
  ) {
    throw new Error('Incomplete stock row');
  }
  return {
    variantId,
    productId,
    productName,
    size,
    color,
    sku,
    published: row.published === true,
    onHand: row.onHand ?? 0,
    reserved: row.reserved ?? 0,
    available: row.available ?? 0,
  };
}

/** One page of the active variants the filter lets through, fewest available first, 50 per page from 0. */
export async function fetchStockOverview(
  { filter, q, categoryId }: StockListFilter,
  page: number,
  signal?: AbortSignal,
): Promise<{ items: StockRow[]; hasMore: boolean }> {
  const { data, response } = await api.GET('/api/admin/catalog/stock', {
    params: {
      query: {
        page,
        ...(filter === 'ALL' ? {} : { filter }),
        ...(q === null ? {} : { q }),
        ...(categoryId === null ? {} : { category: categoryId }),
      },
    },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (data === undefined) {
    throw failed('Stock overview', response);
  }
  return { items: (data.items ?? []).map(toStockRow), hasMore: data.hasMore === true };
}
