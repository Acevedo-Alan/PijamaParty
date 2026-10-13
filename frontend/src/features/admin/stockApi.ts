import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
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
