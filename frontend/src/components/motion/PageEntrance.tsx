import { m } from 'motion/react';
import type { ReactNode } from 'react';
import { useEntranceMotion } from './useActionMotion.ts';

/**
 * A page of the store, which rises and fades in when the visitor moves to it (T-E7-17). The store's first page shows
 * in place: Motion's features load after the first paint, so its largest paint is never delayed (RNF-11). Keyed by the
 * page, so a new page enters; the account area keys its own content, its navigation staying in place (T-E7-60).
 */
export function PageEntrance({ children }: { children: ReactNode }) {
  return <m.div {...useEntranceMotion()}>{children}</m.div>;
}
