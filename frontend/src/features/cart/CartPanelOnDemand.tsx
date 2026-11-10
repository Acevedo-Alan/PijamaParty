import { lazy, Suspense, useEffect, useState } from 'react';
import { useCartPanel } from './CartPanelContext.tsx';

const loadPanel = () => import('./CartPanel.tsx');
const CartPanel = lazy(async () => ({ default: (await loadPanel()).CartPanel }));

/** Fetches the panel's code when the browser is idle; Safari has no requestIdleCallback, so 2 s later there. */
function prefetchWhenIdle(): void {
  const prefetch = () => {
    void loadPanel();
  };
  const idle: { requestIdleCallback?: (callback: () => void) => number } = window;
  if (idle.requestIdleCallback === undefined) {
    window.setTimeout(prefetch, 2_000);
  } else {
    idle.requestIdleCallback(prefetch);
  }
}

/**
 * T-E6-02 (RNF-11): the side panel of the cart (UC-08) is downloaded after the first paint, not with the page's own
 * code. It is fetched once the page has loaded and the browser is idle (T-E6-28): an idle moment before the load, while
 * the home still waits for its banner, put the panel's work before the page's largest paint. The first opening rarely
 * waits for it. It mounts when it first opens and stays mounted, so closing it still plays its exit and gives the focus
 * back.
 */
export function CartPanelOnDemand() {
  const { isOpen } = useCartPanel();
  const [wanted, setWanted] = useState(false);
  useEffect(() => {
    if (document.readyState === 'complete') {
      prefetchWhenIdle();
      return undefined;
    }
    window.addEventListener('load', prefetchWhenIdle, { once: true });
    return () => {
      window.removeEventListener('load', prefetchWhenIdle);
    };
  }, []);
  if (isOpen && !wanted) {
    setWanted(true);
  }
  return wanted ? (
    <Suspense fallback={null}>
      <CartPanel />
    </Suspense>
  ) : null;
}
