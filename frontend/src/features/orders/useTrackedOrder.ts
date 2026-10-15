import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { correctEmail, fetchTrackedOrder, retryPayment } from './orderTrackingApi.ts';

export const trackedOrderKey = (token: string) => ['orders', 'tracked', token] as const;

/** How often the order is read again while an approved payment is being confirmed (T-E3-11). */
export const CONFIRMING_EVERY_MS = 5_000;

/**
 * The order of the tracking link (UC-14). With `pollWhileAwaiting` it is read again every 5 seconds while it is
 * still awaiting payment, until the webhook confirms the payment the buyer just made.
 */
export function useTrackedOrder(token: string, pollWhileAwaiting = false) {
  return useQuery({
    queryKey: trackedOrderKey(token),
    queryFn: ({ signal }) => fetchTrackedOrder(token, signal),
    refetchInterval: (query) => {
      const answer = query.state.data;
      return pollWhileAwaiting && answer?.status === 'found' && answer.order.status === 'AWAITING_PAYMENT'
        ? CONFIRMING_EVERY_MS
        : false;
    },
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

/**
 * Corrects the email of the order (UC-17, T-E3-23); an order that closed meanwhile, or is no longer found, is read
 * again, to show why.
 */
export function useCorrectEmail(token: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (email: string) => correctEmail(token, email),
    onSuccess: (answer) => {
      if (answer.status === 'closed' || answer.status === 'notFound') {
        void queryClient.invalidateQueries({ queryKey: trackedOrderKey(token) });
      }
    },
  });
}
