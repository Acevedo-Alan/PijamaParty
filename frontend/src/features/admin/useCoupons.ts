import { useQuery } from '@tanstack/react-query';
import { fetchCoupons } from './couponsApi.ts';

export const COUPONS_KEY = ['admin', 'coupons'] as const;

/** Every coupon, active or not, newest first, with its uses (UC-11). */
export function useCoupons() {
  return useQuery({ queryKey: COUPONS_KEY, queryFn: ({ signal }) => fetchCoupons(signal) });
}
