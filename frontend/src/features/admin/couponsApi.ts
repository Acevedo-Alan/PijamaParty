import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn, problemProperty, problemText } from './adminSession.ts';

type AdminCouponResponse = components['schemas']['AdminCouponResponse'];

export type CouponType = 'PERCENTAGE' | 'FIXED_AMOUNT';

/** The fields the server can refuse (UC-11 `fields`). */
export type CouponField = 'CODE' | 'DISCOUNT_VALUE' | 'VALIDITY' | 'MAX_USES' | 'MINIMUM_PURCHASE';

const COUPON_FIELDS: readonly CouponField[] = ['CODE', 'DISCOUNT_VALUE', 'VALIDITY', 'MAX_USES', 'MINIMUM_PURCHASE'];

/** What the administrator types for a coupon; dates as ISO days ("2026-12-01"). */
export interface CouponData {
  code: string;
  type: CouponType;
  /** The percent, or the cents of a fixed amount. */
  discountValue: number;
  validFrom: string | null;
  validUntil: string | null;
  maxUses: number | null;
  minimumPurchaseCents: number | null;
}

export interface Coupon extends CouponData {
  id: string;
  active: boolean;
  /** Confirmed orders that used it (E3). */
  uses: number;
}

/** The answer of creating or editing a coupon (UC-11). */
export type SaveAnswer =
  | { status: 'saved' }
  | { status: 'codeAlreadyUsed'; code: string }
  | { status: 'invalid'; fields: CouponField[] }
  | { status: 'notFound' };

export type DeleteAnswer = { status: 'deleted' } | { status: 'hasUses' } | { status: 'notFound' };

function toCoupon(body: AdminCouponResponse): Coupon {
  const { id, code, type, discountValue, active, uses } = body;
  if (id === undefined || code === undefined || type === undefined || discountValue === undefined) {
    throw new Error('Incomplete coupon');
  }
  return {
    id,
    code,
    type,
    discountValue,
    validFrom: body.validFrom ?? null,
    validUntil: body.validUntil ?? null,
    maxUses: body.maxUses ?? null,
    minimumPurchaseCents: body.minimumPurchaseCents ?? null,
    active: active === true,
    uses: uses ?? 0,
  };
}

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

/** Every coupon, active or not, newest first, with its uses. */
export async function fetchCoupons(signal?: AbortSignal): Promise<Coupon[]> {
  const { data, response } = await api.GET('/api/admin/coupons', { signal: signal ?? null });
  ensureSignedIn(response);
  if (data === undefined) {
    throw failed('Coupon list', response);
  }
  return data.map(toCoupon);
}

function invalidFields(error: unknown): CouponField[] {
  const fields = problemProperty(error, 'fields');
  return Array.isArray(fields)
    ? COUPON_FIELDS.filter((field) => fields.includes(field))
    : [];
}

function saveAnswer(error: unknown, response: Response, action: string): SaveAnswer {
  ensureSignedIn(response);
  switch (response.status) {
    case 404:
      return { status: 'notFound' };
    case 409:
      return { status: 'codeAlreadyUsed', code: problemText(error, 'code') };
    case 422:
      return { status: 'invalid', fields: invalidFields(error) };
    default:
      if (!response.ok) {
        throw failed(action, response);
      }
      return { status: 'saved' };
  }
}

export async function createCoupon(coupon: CouponData): Promise<SaveAnswer> {
  const { error, response } = await api.POST('/api/admin/coupons', { body: coupon });
  return saveAnswer(error, response, 'Coupon creation');
}

export async function updateCoupon(id: string, coupon: CouponData): Promise<SaveAnswer> {
  const { error, response } = await api.PUT('/api/admin/coupons/{id}', {
    params: { path: { id } },
    body: coupon,
  });
  return saveAnswer(error, response, 'Coupon edit');
}

/** Activates or deactivates a coupon; false when it no longer exists. */
export async function setCouponActive(id: string, active: boolean): Promise<boolean> {
  const options = { params: { path: { id } } };
  const { response } = active
    ? await api.POST('/api/admin/coupons/{id}/activate', options)
    : await api.POST('/api/admin/coupons/{id}/deactivate', options);
  ensureSignedIn(response);
  if (response.status === 404) {
    return false;
  }
  if (!response.ok) {
    throw failed('Coupon state change', response);
  }
  return true;
}

export async function deleteCoupon(id: string): Promise<DeleteAnswer> {
  const { response } = await api.DELETE('/api/admin/coupons/{id}', { params: { path: { id } } });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (response.status === 409) {
    return { status: 'hasUses' };
  }
  if (!response.ok) {
    throw failed('Coupon deletion', response);
  }
  return { status: 'deleted' };
}
