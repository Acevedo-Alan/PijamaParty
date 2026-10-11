import { QueryClient } from '@tanstack/react-query';

/**
 * Server state (ARCHITECTURE.md section 11). Expected business answers such as an unknown category come back as
 * data, so a retry only follows an unexpected failure.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: 60_000, retry: 1, refetchOnWindowFocus: false },
    },
  });
}
