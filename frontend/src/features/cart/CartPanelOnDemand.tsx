import { lazy, Suspense, useEffect, useState } from 'react';
import { useCartPanel } from './CartPanelContext.tsx';

const loadPanel = () => import('./CartPanel.tsx');
const CartPanel = lazy(async () => ({ default: (await loadPanel()).CartPanel }));

/**
 * T-E6-02 (RNF-11): the side panel of the cart (UC-08) is downloaded after the first paint, not with the page's own
 * code: the browser fetches it once idle, so the first opening rarely waits for it. It mounts when it first opens and
 * stays mounted, so closing it still plays its exit and gives the focus back.
 */
export function CartPanelOnDemand() {
  const { isOpen } = useCartPanel();
  const [wanted, setWanted] = useState(false);
  useEffect(() => {
    // Safari has no requestIdleCallback.
    const prefetch = () => {
      void loadPanel();
    };
    const idle: { requestIdleCallback?: (callback: () => void) => number } = window;
    if (idle.requestIdleCallback === undefined) {
      window.setTimeout(prefetch, 2_000);
    } else {
      idle.requestIdleCallback(prefetch);
    }
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
