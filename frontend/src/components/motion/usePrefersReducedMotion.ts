import { useMediaQuery } from '../useMediaQuery.ts';

/** The visitor's reduced motion setting, kept current when it changes. */
export function usePrefersReducedMotion(): boolean {
  return useMediaQuery('(prefers-reduced-motion: reduce)');
}
