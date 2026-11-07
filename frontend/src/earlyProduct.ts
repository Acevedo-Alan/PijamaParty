/**
 * T-E6-02 (RNF-11): its own tiny entry of index.html, run while the app's bundle is still downloading. On a product
 * page it asks for the product right away, so the main photo's address is known sooner and the photo starts loading
 * sooner (the page's largest paint). The app takes the answer in `fetchProductDetail`; without it, the app asks itself.
 * It imports nothing, so it stays one small file that never waits for another.
 */
export interface EarlyProduct {
  slug: string;
  response: Promise<Response>;
}

declare global {
  interface Window {
    earlyProduct?: EarlyProduct | undefined;
  }
}

const match = /^\/producto\/([^/]+)\/?$/.exec(window.location.pathname);
if (match?.[1] !== undefined) {
  try {
    const slug = decodeURIComponent(match[1]);
    window.earlyProduct = {
      slug,
      response: fetch(`/api/catalog/products/${encodeURIComponent(slug)}`, { credentials: 'same-origin' }),
    };
  } catch {
    // A malformed address: the app answers it as it always did.
    window.earlyProduct = undefined;
  }
}
