import { lazy, Suspense, type ComponentProps } from 'react';
import './chibiBox.css';

const Chibi = lazy(() => import('./Chibi.tsx').then(({ Chibi: component }) => ({ default: component })));

/**
 * The store's chibi in a chunk of its own, for pages of the first load (the search and the 404 page, T-E7-49): the
 * drawing and its stylesheet are fetched only when one of their empty states shows. Until then an empty box with the
 * same class holds its place, so nothing shifts when it arrives.
 */
export function LazyChibi(props: ComponentProps<typeof Chibi>) {
  return (
    <Suspense fallback={<span className={props.className} aria-hidden="true" />}>
      <Chibi {...props} />
    </Suspense>
  );
}
