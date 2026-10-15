import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';

type TrackedOrderResponse = components['schemas']['TrackedOrderResponse'];
type TrackedItemResponse = components['schemas']['TrackedItemResponse'];

export type OrderStatus = NonNullable<TrackedOrderResponse['status']>;
export type Province = NonNullable<TrackedOrderResponse['province']>;

export interface TrackedItem {
  /** Identifies the item in analytics (RNF-25). */
  sku: string;
  productName: string;
  size: string;
  color: string;
  quantity: number;
  unitPriceCents: number;
  lineTotalCents: number;
}

/**
 * The order of a tracking link (UC-14, T-E3-08). Only what following it needs: the API never sends the national ID,
 * the email, the phone or the street.
 */
export interface TrackedOrder {
  number: number;
  status: OrderStatus;
  buyerFirstName: string;
  items: TrackedItem[];
  subtotalCents: number;
  discountCents: number;
  couponCode: string | null;
  shippingCents: number;
  totalCents: number;
  city: string;
  province: Province;
  reservationExpiresAt: Date;
  shipmentTrackingNumber: string | null;
  /** Awaiting payment with its stock still reserved. */
  canRetryPayment: boolean;
}

/** GET /api/orders/{token}. An unknown token is an expected answer, not an error. */
export type TrackedOrderAnswer = { status: 'found'; order: TrackedOrder } | { status: 'notFound' };

/** POST /api/orders/{token}/retry-payment: where to pay, or why not now. */
export type RetryAnswer =
  | { status: 'opened'; paymentUrl: string }
  | { status: 'notPayable' }
  | { status: 'providerUnavailable' }
  | { status: 'tooManyAttempts' }
  | { status: 'notFound' };

function toItem({
  sku,
  productName,
  size,
  color,
  quantity,
  unitPriceCents,
  lineTotalCents,
}: TrackedItemResponse): TrackedItem {
  if (
    sku === undefined ||
    productName === undefined ||
    size === undefined ||
    color === undefined ||
    quantity === undefined ||
    unitPriceCents === undefined ||
    lineTotalCents === undefined
  ) {
    throw new Error('Incomplete item in the tracked order');
  }
  return { sku, productName, size, color, quantity, unitPriceCents, lineTotalCents };
}

function toOrder(data: TrackedOrderResponse): TrackedOrder {
  const {
    number,
    status,
    buyerFirstName,
    subtotalCents,
    discountCents,
    shippingCents,
    totalCents,
    city,
    province,
    reservationExpiresAt,
    canRetryPayment,
  } = data;
  if (
    number === undefined ||
    status === undefined ||
    buyerFirstName === undefined ||
    subtotalCents === undefined ||
    discountCents === undefined ||
    shippingCents === undefined ||
    totalCents === undefined ||
    city === undefined ||
    province === undefined ||
    reservationExpiresAt === undefined ||
    canRetryPayment === undefined
  ) {
    throw new Error('Incomplete tracked order');
  }
  return {
    number,
    status,
    buyerFirstName,
    items: (data.items ?? []).map(toItem),
    subtotalCents,
    discountCents,
    couponCode: data.couponCode ?? null,
    shippingCents,
    totalCents,
    city,
    province,
    reservationExpiresAt: new Date(reservationExpiresAt),
    shipmentTrackingNumber: data.shipmentTrackingNumber ?? null,
    canRetryPayment,
  };
}

export async function fetchTrackedOrder(token: string, signal?: AbortSignal): Promise<TrackedOrderAnswer> {
  const { data, response } = await api.GET('/api/orders/{token}', {
    params: { path: { token } },
    signal: signal ?? null,
  });
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (data === undefined) {
    throw new Error(`Tracking the order failed with status ${String(response.status)}`);
  }
  return { status: 'found', order: toOrder(data) };
}

export async function retryPayment(token: string): Promise<RetryAnswer> {
  const { data, response } = await api.POST('/api/orders/{token}/retry-payment', {
    params: { path: { token } },
  });
  if (data?.paymentUrl !== undefined) {
    return { status: 'opened', paymentUrl: data.paymentUrl };
  }
  switch (response.status) {
    case 409:
      return { status: 'notPayable' };
    case 503:
      return { status: 'providerUnavailable' };
    case 429:
      return { status: 'tooManyAttempts' };
    case 404:
      return { status: 'notFound' };
    default:
      throw new Error(`Retrying the payment failed with status ${String(response.status)}`);
  }
}

/** PATCH /api/orders/{token}/email (UC-17, T-E3-23): the email was corrected, or why not. */
export type EmailCorrectionAnswer =
  | { status: 'corrected'; confirmationResent: boolean }
  | { status: 'invalidEmail' }
  | { status: 'closed' }
  | { status: 'tooManyAttempts' }
  | { status: 'notFound' };

export async function correctEmail(token: string, email: string): Promise<EmailCorrectionAnswer> {
  const { data, response } = await api.PATCH('/api/orders/{token}/email', {
    params: { path: { token } },
    body: { email },
  });
  if (data?.confirmationResent !== undefined) {
    return { status: 'corrected', confirmationResent: data.confirmationResent };
  }
  switch (response.status) {
    case 422:
      return { status: 'invalidEmail' };
    case 409:
      return { status: 'closed' };
    case 429:
      return { status: 'tooManyAttempts' };
    case 404:
      return { status: 'notFound' };
    default:
      throw new Error(`Correcting the email failed with status ${String(response.status)}`);
  }
}
