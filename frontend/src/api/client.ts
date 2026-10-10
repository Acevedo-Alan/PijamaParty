import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './schema.d.ts';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

/** The CSRF token the backend sets in the readable XSRF-TOKEN cookie (RNF-29.1). */
export function readCsrfToken(cookies: string = document.cookie): string | undefined {
  for (const cookie of cookies.split(';')) {
    const [name, ...value] = cookie.trim().split('=');
    if (name === 'XSRF-TOKEN') {
      return decodeURIComponent(value.join('='));
    }
  }
  return undefined;
}

/**
 * Sends the cookie's CSRF token back raw in X-XSRF-TOKEN on every state-changing request to our own origin, never
 * to a third party even if a call overrides the base URL.
 */
export const csrfMiddleware: Middleware = {
  onRequest({ request }) {
    const token = readCsrfToken();
    const sameOrigin = new URL(request.url, window.location.href).origin === window.location.origin;
    if (!SAFE_METHODS.has(request.method) && sameOrigin && token !== undefined) {
      request.headers.set('X-XSRF-TOKEN', token);
    }
    return request;
  },
};

/**
 * Typed client generated from the backend OpenAPI contract (npm run api:generate). Same origin, so cookies are
 * first-party; the server is the source of truth for prices, discounts, shipping and stock.
 */
export const api = createClient<paths>({ baseUrl: '/', credentials: 'same-origin' });
api.use(csrfMiddleware);
