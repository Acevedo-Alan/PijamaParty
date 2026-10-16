import { useQuery } from '@tanstack/react-query';
import { fetchCustomer, type Customer } from './accountApi.ts';

/** The query key of the signed-in customer; signing in and out set it. */
export const CUSTOMER_KEY = ['account', 'me'] as const;

/** The signed-in customer: null when signed out, undefined while it is not known yet. */
export function useCustomer(): Customer | null | undefined {
  const { data } = useQuery({
    queryKey: CUSTOMER_KEY,
    queryFn: ({ signal }) => fetchCustomer(signal),
    staleTime: 5 * 60 * 1000,
  });
  return data;
}

/** The first word of the name, for the greeting. */
export function firstName(name: string): string {
  return name.trim().split(/\s+/, 1)[0] ?? name;
}
