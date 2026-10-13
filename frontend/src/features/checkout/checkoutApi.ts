import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import type { CouponStatus } from '../cart/cartApi.ts';
import { isProvince, type Province } from './checkoutForm.ts';

type ShippingCostResponse = components['schemas']['ShippingCostResponse'];
type PlaceOrderRequest = components['schemas']['PlaceOrderRequest'];

/** The step-1 summary as the server calculates it (R-01, R-02): total = subtotal − discount + shipping. */
export interface ShippingCost {
  province: Province;
  subtotalCents: number;
  discountCents: number;
  shippingCents: number;
  totalCents: number;
}

/** GET /api/checkout/shipping-cost. A province without shipping and an empty cart are expected answers. */
export type ShippingCostAnswer =
  | { status: 'quoted'; cost: ShippingCost }
  | { status: 'noShipping' }
  | { status: 'emptyCart' };

function toCost(data: ShippingCostResponse): ShippingCost {
  const { province, subtotalCents, discountCents, shippingCents, totalCents } = data;
  if (
    province === undefined ||
    subtotalCents === undefined ||
    discountCents === undefined ||
    shippingCents === undefined ||
    totalCents === undefined
  ) {
    throw new Error('Incomplete shipping cost');
  }
  return { province, subtotalCents, discountCents, shippingCents, totalCents };
}

export async function fetchShippingCost(province: Province, signal?: AbortSignal): Promise<ShippingCostAnswer> {
  const { data, response } = await api.GET('/api/checkout/shipping-cost', {
    params: { query: { province } },
    signal: signal ?? null,
  });
  if (data !== undefined) {
    return { status: 'quoted', cost: toCost(data) };
  }
  switch (response.status) {
    case 422:
      return { status: 'noShipping' };
    case 409:
      return { status: 'emptyCart' };
    default:
      throw new Error(`The shipping cost failed with status ${String(response.status)}`);
  }
}

/** The order placed: where to pay, and its tracking token, shown only in this answer. */
export interface PlacedOrder {
  number: number;
  trackingToken: string;
  /** The provider's checkout; null when it could not be reached, and then the tracking page retries. */
  paymentUrl: string | null;
}

/**
 * POST /api/checkout. Every refusal of UC-13 and UC-14 is an expected answer; `unknown` means no answer was read
 * (a network error or a server failure), so the same Idempotency-Key may be repeated.
 */
export type PlaceOrderAnswer =
  | { status: 'placed'; order: PlacedOrder }
  | { status: 'emptyCart' }
  | { status: 'cartNotReady' }
  | { status: 'couponNotApplicable'; couponStatus: CouponStatus | null }
  | { status: 'invalidBuyerData'; fields: unknown[] }
  | { status: 'termsNotAccepted' }
  | { status: 'noShipping'; province: Province | null }
  | { status: 'tooManyAttempts' }
  | { status: 'unknown' };

function propertiesOf(problem: unknown): Record<string, unknown> {
  return typeof problem === 'object' && problem !== null ? (problem as Record<string, unknown>) : {};
}

const COUPON_STATUSES: readonly string[] = ['APPLICABLE', 'INACTIVE', 'NOT_STARTED', 'EXPIRED', 'USED_UP', 'BELOW_MINIMUM'];

function toRefusal(problem: unknown): PlaceOrderAnswer {
  const body = propertiesOf(problem);
  switch (body.problem) {
    case 'EMPTY_CART':
      return { status: 'emptyCart' };
    case 'CART_NOT_READY':
    case 'INSUFFICIENT_STOCK':
      return { status: 'cartNotReady' };
    case 'COUPON_NOT_APPLICABLE': {
      const couponStatus = typeof body.couponStatus === 'string' && COUPON_STATUSES.includes(body.couponStatus)
        ? (body.couponStatus as CouponStatus)
        : null;
      return { status: 'couponNotApplicable', couponStatus };
    }
    case 'INVALID_BUYER_DATA':
      return { status: 'invalidBuyerData', fields: Array.isArray(body.fields) ? body.fields : [] };
    case 'TERMS_NOT_ACCEPTED':
      return { status: 'termsNotAccepted' };
    case 'NO_SHIPPING':
      return {
        status: 'noShipping',
        province: typeof body.province === 'string' && isProvince(body.province) ? body.province : null,
      };
    default:
      throw new Error('Unknown checkout refusal');
  }
}

export async function placeOrder(idempotencyKey: string, body: PlaceOrderRequest): Promise<PlaceOrderAnswer> {
  let result;
  try {
    result = await api.POST('/api/checkout', { params: { header: { 'Idempotency-Key': idempotencyKey } }, body });
  } catch {
    return { status: 'unknown' };
  }
  const { data, error, response } = result;
  if (data !== undefined) {
    const { number, trackingToken } = data;
    if (number === undefined || trackingToken === undefined) {
      throw new Error('Incomplete placed order');
    }
    return { status: 'placed', order: { number, trackingToken, paymentUrl: data.paymentUrl } };
  }
  if (response.status === 409 || response.status === 422) {
    return toRefusal(error);
  }
  if (response.status === 429) {
    return { status: 'tooManyAttempts' };
  }
  if (response.status >= 500) {
    return { status: 'unknown' };
  }
  throw new Error(`The checkout failed with status ${String(response.status)}`);
}
