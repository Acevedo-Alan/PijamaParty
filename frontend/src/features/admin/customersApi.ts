import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn } from './adminSession.ts';

type CustomerRowResponse = components['schemas']['CustomerRowResponse'];

/** A row of "Clientes" (UC-34). Personal data: never logged or sent to analytics. */
export interface CustomerSummary {
  id: string;
  name: string;
  email: string;
  registeredAt: Date;
  orders: number;
}

/** T-E8-10: which customers the list shows; both dates are store dates (yyyy-MM-dd), inclusive. */
export interface CustomerListFilter {
  q: string | null;
  from: string | null;
  to: string | null;
}

function toCustomer({ id, name, email, registeredAt, orders }: CustomerRowResponse): CustomerSummary {
  if (id === undefined || name === undefined || email === undefined || registeredAt === undefined) {
    throw new Error('Incomplete customer');
  }
  return { id, name, email, registeredAt: new Date(registeredAt), orders: orders ?? 0 };
}

/** One page of the customers the filter lets through, newest first, 50 per page from 0. */
export async function fetchCustomers(
  { q, from, to }: CustomerListFilter,
  page: number,
  signal?: AbortSignal,
): Promise<{ items: CustomerSummary[]; hasMore: boolean }> {
  const { data, response } = await api.GET('/api/admin/customers', {
    params: {
      query: {
        page,
        ...(q === null ? {} : { q }),
        ...(from === null ? {} : { from }),
        ...(to === null ? {} : { to }),
      },
    },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (data === undefined) {
    throw new Error(`Customer list failed with status ${String(response.status)}`);
  }
  return { items: (data.items ?? []).map(toCustomer), hasMore: data.hasMore === true };
}

type CustomerProfileResponse = components['schemas']['CustomerProfileResponse'];
type CustomerAddressResponse = components['schemas']['CustomerAddressResponse'];
type CustomerOrderResponse = components['schemas']['CustomerOrderResponse'];

export type SignInProvider = NonNullable<CustomerProfileResponse['providers']>[number];
export type CustomerOrderStatus = NonNullable<CustomerOrderResponse['status']>;

/** A saved address of the customer's book (UC-20). Personal data: never logged or sent to analytics. */
export interface CustomerAddress {
  id: string;
  street: string;
  number: string;
  floor: string | null;
  apartment: string | null;
  city: string;
  /** One of the 24 jurisdictions, e.g. SANTA_FE. */
  province: string;
  postalCode: string;
  primary: boolean;
}

/** An order of the customer, as a row of "Pedidos". */
export interface CustomerOrder {
  id: string;
  number: number;
  createdAt: Date;
  status: CustomerOrderStatus;
  totalCents: number;
}

/** UC-34 (T-E8-11): a customer's profile. Personal data: never logged or sent to analytics. */
export interface CustomerProfile {
  id: string;
  name: string;
  email: string;
  emailVerified: boolean;
  /** Digits only; null when the customer saved none. */
  phone: string | null;
  registeredAt: Date;
  providers: SignInProvider[];
  /** The main one first. */
  addresses: CustomerAddress[];
  /** One page of 20, newest first. */
  orders: CustomerOrder[];
  hasMoreOrders: boolean;
  orderCount: number;
  /** The orders paid and neither cancelled nor expired. */
  purchases: number;
  spentCents: number;
  lastPurchaseAt: Date | null;
}

export type CustomerProfileAnswer = { status: 'found'; profile: CustomerProfile } | { status: 'notFound' };

function toAddress({
  id,
  street,
  number,
  floor,
  apartment,
  city,
  province,
  postalCode,
  primary,
}: CustomerAddressResponse): CustomerAddress {
  if (
    id === undefined ||
    street === undefined ||
    number === undefined ||
    city === undefined ||
    province === undefined ||
    postalCode === undefined
  ) {
    throw new Error('Incomplete address');
  }
  return {
    id,
    street,
    number,
    floor: floor ?? null,
    apartment: apartment ?? null,
    city,
    province,
    postalCode,
    primary: primary === true,
  };
}

function toOrder({ id, number, createdAt, status, totalCents }: CustomerOrderResponse): CustomerOrder {
  if (id === undefined || number === undefined || createdAt === undefined || status === undefined) {
    throw new Error('Incomplete order');
  }
  return { id, number, createdAt: new Date(createdAt), status, totalCents: totalCents ?? 0 };
}

function toProfile(data: CustomerProfileResponse): CustomerProfile {
  const { id, name, email, registeredAt } = data;
  if (id === undefined || name === undefined || email === undefined || registeredAt === undefined) {
    throw new Error('Incomplete customer');
  }
  return {
    id,
    name,
    email,
    emailVerified: data.emailVerified === true,
    phone: data.phone ?? null,
    registeredAt: new Date(registeredAt),
    providers: data.providers ?? [],
    addresses: (data.addresses ?? []).map(toAddress),
    orders: (data.orders ?? []).map(toOrder),
    hasMoreOrders: data.hasMoreOrders === true,
    orderCount: data.orderCount ?? 0,
    purchases: data.purchases ?? 0,
    spentCents: data.spentCents ?? 0,
    lastPurchaseAt: typeof data.lastPurchaseAt === 'string' ? new Date(data.lastPurchaseAt) : null,
  };
}

/**
 * A customer's profile with one page of its orders, 20 per page from 0. Opening it (page 0) is recorded in "Actividad"
 * by the server; a later page is not.
 */
export async function fetchCustomerProfile(
  id: string,
  ordersPage: number,
  signal?: AbortSignal,
): Promise<CustomerProfileAnswer> {
  const { data, response } = await api.GET('/api/admin/customers/{id}', {
    params: { path: { id }, query: { ordersPage } },
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (response.status === 404) {
    return { status: 'notFound' };
  }
  if (data === undefined) {
    throw new Error(`Customer profile failed with status ${String(response.status)}`);
  }
  return { status: 'found', profile: toProfile(data) };
}
