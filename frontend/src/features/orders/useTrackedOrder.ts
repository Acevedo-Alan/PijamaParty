import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchTrackedOrder, retryPayment } from './orderTrackingApi.ts';

export const trackedOrderKey = (token: string) => ['orders', 'tracked', token] as const;

/** The order of the tracking link (UC-14). */
export function useTrackedOrder(token: string) {
  return useQuery({
    queryKey: trackedOrderKey(token),
    queryFn: ({ signal }) => fetchTrackedOrder(token, signal),
  });
}

/** Pays again from the tracking link; an order that can no longer be paid is read again, to show why. */
export function useRetryPayment(token: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => retryPayment(token),
    onSuccess: (answer) => {
      if (answer.status === 'notPayable' || answer.status === 'notFound') {
        void queryClient.invalidateQueries({ queryKey: trackedOrderKey(token) });
      }
    },
  });
}
