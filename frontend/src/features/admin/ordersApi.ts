import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn, problemProperty, problemText } from './adminSession.ts';

type SummaryResponse = components['schemas']['OrderSummaryResponse'];
type DetailResponse = components['schemas']['AdminOrderResponse'];

export type OrderStatus = components['schemas']['OrderStatusChangeRequest']['to'];
export type OrderAlert = NonNullable<DetailResponse['alerts']>[number];
export type Province = NonNullable<SummaryResponse['province']>;

/** The seven states of an order, in the order of the state diagram (UC-18). */
export const ORDER_STATUSES: readonly OrderStatus[] = [
  'AWAITING_PAYMENT',
  'PAID',
  'PREPARING',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
  'EXPIRED',
];

/** A row of the back-office list (UC-18). */
export interface OrderSummary {
  id: string;
  number: number;
  createdAt: Date;
  status: OrderStatus;
  buyerName: string;
  city: string;
  province: Province;
  totalCents: number;
}

export interface AdminOrderLine {
  productName: string;
  size: string;
  color: string;
  quantity: number;
  unitPriceCents: number;
  amountCents: number;
}

/** An order as the back office shows it (UC-18): what it may move to, and its alerts, most urgent first. */
export interface AdminOrder {
  id: string;
  number: number;
  status: OrderStatus;
  nextStatuses: OrderStatus[];
  createdAt: Date;
  deliveredAt: Date | null;
  buyer: { name: string; email: string; phone: string; nationalId: string };
  address: {
    street: string;
    number: string;
    floor: string | null;
    apartment: string | null;
    city: string;
    province: string;
    postalCode: string;
  };
  lines: AdminOrderLine[];
  subtotalCents: number;
  discountCents: number;
  couponCode: string | null;
  shippingCents: number;
  totalCents: number;
  paymentProvider: string;
  shipmentTrackingNumber: string | null;
  /** Its received withdrawal request (UC-29), if any. */
  withdrawal: { code: string; outOfTerm: boolean } | null;
  alerts: OrderAlert[];
}

export type OrderAnswer = { status: 'found'; order: AdminOrder } | { status: 'notFound' };

/** What the administrator asks: from the status it saw to another one (UC-18). */
export interface StatusChange {
  from: OrderStatus;
  to: OrderStatus;
  /** Only for SHIPPED. */
  shipmentTrackingNumber?: string;
  /** Only when cancelling a PAID or PREPARING order. */
  restock?: boolean;
}

export type ChangeProblem =
  | 'INVALID_TRANSITION'
  | 'MISSING_TRACKING_NUMBER'
  | 'INVALID_TRACKING_NUMBER'
  | 'RESTOCK_DECISION_REQUIRED'
  | 'UNEXPECTED_FIELD';

export type ChangeAnswer =
  | { status: 'changed'; orderStatus: OrderStatus }
  /** Someone or something changed the order since it was read: read it again. */
  | { status: 'stale' }
  | { status: 'refused'; problem: ChangeProblem }
  | { status: 'notFound' };

export type TrackingNumberAnswer =
  | { status: 'corrected'; shipmentTrackingNumber: string }
  | { status: 'notShipped' }
  | { status: 'invalid' }
  | { status: 'notFound' };

const CHANGE_PROBLEMS: readonly ChangeProblem[] = [
  'INVALID_TRANSITION',
  'MISSING_TRACKING_NUMBER',
  'INVALID_TRACKING_NUMBER',
  'RESTOCK_DECISION_REQUIRED',
  'UNEXPECTED_FIELD',
];

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

function toSummary(row: SummaryResponse): OrderSummary {
  const { id, number, createdAt, status, buyerName, city, province, totalCents } = row;
  if (
    id === undefined ||
    number === undefined ||
    createdAt === undefined ||
    status === undefined ||
    buyerName === undefined ||
    city === undefined ||
    province === undefined ||
    totalCents === undefined
  ) {
    throw new Error('Incomplete order summary');
  }
  return { id, number, createdAt: new Date(createdAt), status, buyerName, city, province, totalCents };
}

function toOrder(body: DetailResponse): AdminOrder {
  const { id, number, status, createdAt, buyer, address } = body;
  if (
    id === undefined ||
    number === undefined ||
    status === undefined ||
    createdAt === undefined ||
    buyer === undefined ||
    address === undefined
  ) {
    throw new Error('Incomplete order');
  }
  return {
    id,
    number,
    status,
    nextStatuses: body.nextStatuses ?? [],
    createdAt: new Date(createdAt),
    deliveredAt: body.deliveredAt ? new Date(body.deliveredAt) : null,
    buyer: {
      // D-13: the order keeps both; the back office shows "Nombre Apellido".
      name: `${buyer.firstName ?? ''} ${buyer.lastName ?? ''}`.trim(),
      email: buyer.email ?? '',
      phone: buyer.phone ?? '',
      nationalId: buyer.nationalId ?? '',
    },
    address: {
      street: address.street ?? '',
      number: address.number ?? '',
      floor: address.floor ?? null,
      apartment: address.apartment ?? null,
      city: address.city ?? '',
      province: address.province ?? '',
      postalCode: address.postalCode ?? '',
    },
    lines: (body.lines ?? []).map((line) => ({
      productName: line.productName ?? '',
      size: line.size ?? '',
      color: line.color ?? '',
      quantity: line.quantity ?? 0,
      unitPriceCents: line.unitPriceCents ?? 0,
      amountCents: line.amountCents ?? 0,
    })),
    subtotalCents: body.subtotalCents ?? 0,
    discountCents: body.discountCents ?? 0,
    couponCode: body.couponCode ?? null,
    shippingCents: body.shippingCents ?? 0,
    totalCents: body.totalCents ?? 0,
    paymentProvider: body.paymentProvider ?? '',
    shipmentTrackingNumber: body.shipmentTrackingNumber ?? null,
    withdrawal: body.withdrawal?.code ? { code: body.withdrawal.code, outOfTerm: body.withdrawal.outOfTerm === true } : null,
    alerts: body.alerts ?? [],
  };
}

/** One page of orders, newest first, only those in {@code status} when given. */
export async function fetchOrders(
  status: OrderStatus | null,
  page: number,
  signal?: AbortSignal,
): Promise<{ items: OrderSummary[]; hasMore: boolean }> {
  const { data, response } = await api.GET('/api/admin/orders', {
    params: { query: status === null ? { page } : { status, page } },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (data === undefined) {
    throw failed('Order list', response);
  }
  return { items: (data.items ?? []).map(toSummary), hasMore: data.hasMore === true };
}

export async function fetchOrder(id: string, signal?: AbortSignal): Promise<OrderAnswer> {
  const { data, response } = await api.GET('/api/admin/orders/{id}', {
    params: { path: { id } },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (data === undefined) {
    throw failed('Order', response);
  }
  return { status: 'found', order: toOrder(data) };
}

export async function changeOrderStatus(id: string, change: StatusChange): Promise<ChangeAnswer> {
  const { data, error, response } = await api.POST('/api/admin/orders/{id}/status', {
    params: { path: { id } },
    body: change,
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  const problem = problemText(error, 'problem');
  if (response.status === 409 && problem === 'ORDER_STATUS_CHANGED') {
    return { status: 'stale' };
  }
  if ((response.status === 409 || response.status === 422) && CHANGE_PROBLEMS.includes(problem as ChangeProblem)) {
    return { status: 'refused', problem: problem as ChangeProblem };
  }
  if (data?.status === undefined) {
    throw failed('Order status', response);
  }
  return { status: 'changed', orderStatus: data.status };
}

export async function correctTrackingNumber(id: string, shipmentTrackingNumber: string): Promise<TrackingNumberAnswer> {
  const { data, error, response } = await api.PUT('/api/admin/orders/{id}/shipment-tracking-number', {
    params: { path: { id } },
    body: { shipmentTrackingNumber },
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (response.status === 409 && problemProperty(error, 'problem') === 'ORDER_NOT_SHIPPED') {
    return { status: 'notShipped' };
  }
  if (response.status === 422) {
    return { status: 'invalid' };
  }
  if (data?.shipmentTrackingNumber === undefined) {
    throw failed('Tracking number', response);
  }
  return { status: 'corrected', shipmentTrackingNumber: data.shipmentTrackingNumber };
}
