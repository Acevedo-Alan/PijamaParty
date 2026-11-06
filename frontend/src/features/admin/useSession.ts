import { useQuery } from '@tanstack/react-query';
import { fetchSession } from './authApi.ts';

export const SESSION_KEY = ['admin', 'session'] as const;

/** Who is signed in to the back office (UC-04). Checked again when a page asks for it after an expiry. */
export const sessionQuery = {
  queryKey: SESSION_KEY,
  queryFn: ({ signal }: { signal: AbortSignal }) => fetchSession(signal),
  staleTime: Infinity,
};

export function useSession() {
  return useQuery(sessionQuery);
}

/** Where the sign-in page returns: a back-office page, never the sign-in page itself or another site. */
export function returnPath(from: unknown): string {
  return typeof from === 'string' && from.startsWith('/admin/') && !from.startsWith('/admin/ingresar')
    ? from
    : '/admin';
}

/** The router state the sign-in page reads. */
export interface SignInState {
  from?: string;
  expired?: boolean;
}
