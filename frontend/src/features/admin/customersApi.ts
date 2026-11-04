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
