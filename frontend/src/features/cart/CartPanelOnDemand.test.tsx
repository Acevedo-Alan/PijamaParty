import { render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import cartPanelSource from './CartPanel.tsx?raw';
import { CartPanelOnDemand } from './CartPanelOnDemand.tsx';
import { CartPanelProvider } from './CartPanelContext.tsx';

/** The document's loading state, as the browser reports it while the page loads. */
function pageIs(state: DocumentReadyState) {
  Object.defineProperty(document, 'readyState', { configurable: true, get: () => state });
}

function renderPrefetcher() {
  const idle = vi.fn();
  vi.stubGlobal('requestIdleCallback', idle);
  render(
    <CartPanelProvider>
      <CartPanelOnDemand />
    </CartPanelProvider>,
  );
  return idle;
}

describe('cart panel prefetch (T-E6-02, T-E6-28)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(document, 'readyState');
  });

  it('given a page still loading, when it renders, then the panel waits for the load event and then for an idle moment', () => {
    pageIs('interactive');
    const idle = renderPrefetcher();

    expect(idle).not.toHaveBeenCalled();
    window.dispatchEvent(new Event('load'));

    expect(idle).toHaveBeenCalledOnce();
  });

  it('given a page already loaded, when it renders, then the panel is fetched at the next idle moment', () => {
    pageIs('complete');
    const idle = renderPrefetcher();

    expect(idle).toHaveBeenCalledOnce();
  });

  it('given the cart panel, when its code is read, then it brings the chibi only on demand', () => {
    expect(cartPanelSource).not.toMatch(/from '\.\.\/\.\.\/components\/chibi\/Chibi\.tsx'/);
    expect(cartPanelSource).toMatch(/from '\.\.\/\.\.\/components\/chibi\/LazyChibi\.tsx'/);
  });
});
