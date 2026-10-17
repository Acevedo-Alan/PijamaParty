import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { isProvince, type Province } from '../checkout/checkoutForm.ts';
import type { OrderStatus } from '../orders/orderTrackingApi.ts';

type MyOrderSummaryResponse = components['schemas']['MyOrderSummaryResponse'];
type MyOrderResponse = components['schemas']['MyOrderResponse'];
type MyOrderItemResponse = components['schemas']['MyOrderItemResponse'];
type MyOrderAddressResponse = components['schemas']['MyOrderAddressResponse'];

/** The query keys of "Mis pedidos"; signing out forgets them. */
export const MY_ORDERS_KEY = ['account', 'orders'] as const;
export const myOrderKey = (id: string) => [...MY_ORDERS_KEY, id] as const;

/** Correo Argentino's tracking page: it has no stable link that carries the number (decided in T-E4-07). */
export const CORREO_TRACKING_URL = 'https://www.correoargentino.com.ar/formularios/e-commerce';

/** An order as a row of "Mis pedidos" (UC-22, T-E4-07). */
export interface MyOrderSummary {
  id: string;
  number: number;
  createdAt: Date;
  status: OrderStatus;
  totalCents: number;
}

export interface MyOrderPage {
  orders: MyOrderSummary[];
  hasMore: boolean;
}

export interface MyOrderItem {
  sku: string;
  productName: string;
  size: string;
  color: string;
  quantity: number;
  unitPriceCents: number;
  lineTotalCents: number;
}

/** The order's own copy of the address (R-04). */
export interface MyOrderAddress {
  street: string;
  number: string;
  floor: string | null;
  apartment: string | null;
  city: string;
  province: Province;
  postalCode: string;
}

/** One order of the customer, with its tracking. */
export interface MyOrder {
  id: string;
  number: number;
  status: OrderStatus;
  createdAt: Date;
  items: MyOrderItem[];
  subtotalCents: number;
  discountCents: number;
  couponCode: string | null;
  shippingCents: number;
  totalCents: number;
  address: MyOrderAddress;
  shipmentTrackingNumber: string | null;
}

/** GET /api/account/orders: null when nobody is signed in as a customer. */
export type MyOrderPageAnswer = MyOrderPage | null;

/** GET /api/account/orders/{id}: another person's order is not found, as an unknown one. */
export type MyOrderAnswer = { status: 'found'; order: MyOrder } | { status: 'notFound' } | { status: 'signedOut' };

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

function toSummary({ id, number, createdAt, status, totalCents }: MyOrderSummaryResponse): MyOrderSummary {
  if (
    id === undefined ||
    number === undefined ||
    createdAt === undefined ||
    status === undefined ||
    totalCents === undefined
  ) {
    throw new Error('Incomplete order in my orders');
  }
  return { id, number, createdAt: new Date(createdAt), status, totalCents };
}

function toItem({
  sku,
  productName,
  size,
  color,
  quantity,
  unitPriceCents,
  lineTotalCents,
}: MyOrderItemResponse): MyOrderItem {
  if (
    sku === undefined ||
    productName === undefined ||
    size === undefined ||
    color === undefined ||
    quantity === undefined ||
    unitPriceCents === undefined ||
    lineTotalCents === undefined
  ) {
    throw new Error('Incomplete item in my order');
  }
  return { sku, productName, size, color, quantity, unitPriceCents, lineTotalCents };
}

function toAddress(data: MyOrderAddressResponse | undefined): MyOrderAddress {
  if (
    data?.street === undefined ||
    data.number === undefined ||
    data.city === undefined ||
    data.province === undefined ||
    !isProvince(data.province) ||
    data.postalCode === undefined
  ) {
    throw new Error('Incomplete address in my order');
  }
  return {
    street: data.street,
    number: data.number,
    floor: data.floor,
    apartment: data.apartment,
    city: data.city,
    province: data.province,
    postalCode: data.postalCode,
  };
}

function toOrder(data: MyOrderResponse): MyOrder {
  const { id, number, status, createdAt, subtotalCents, discountCents, shippingCents, totalCents } = data;
  if (
    id === undefined ||
    number === undefined ||
    status === undefined ||
    createdAt === undefined ||
    subtotalCents === undefined ||
    discountCents === undefined ||
    shippingCents === undefined ||
    totalCents === undefined
  ) {
    throw new Error('Incomplete order');
  }
  return {
    id,
    number,
    status,
    createdAt: new Date(createdAt),
    items: (data.items ?? []).map(toItem),
    subtotalCents,
    discountCents,
    couponCode: data.couponCode,
    shippingCents,
    totalCents,
    address: toAddress(data.address),
    shipmentTrackingNumber: data.shipmentTrackingNumber,
  };
}

export async function fetchMyOrders(page: number, signal?: AbortSignal): Promise<MyOrderPageAnswer> {
  const { data, response } = await api.GET('/api/account/orders', {
    params: { query: { page } },
    signal: signal ?? null,
  });
  if (response.status === 401 || response.status === 403) {
    return null;
  }
  if (response.status !== 200 || data?.orders === undefined || data.hasMore === undefined) {
    throw failed('My orders', response);
  }
  return { orders: data.orders.map(toSummary), hasMore: data.hasMore };
}

export async function fetchMyOrder(id: string, signal?: AbortSignal): Promise<MyOrderAnswer> {
  const { data, response } = await api.GET('/api/account/orders/{id}', {
    params: { path: { id } },
    signal: signal ?? null,
  });
  switch (response.status) {
    case 200:
      if (data === undefined) {
        throw failed('My order', response);
      }
      return { status: 'found', order: toOrder(data) };
    case 401:
    case 403:
      return { status: 'signedOut' };
    case 404:
    case 400:
      // A malformed id is no order either.
      return { status: 'notFound' };
    default:
      throw failed('My order', response);
  }
}
