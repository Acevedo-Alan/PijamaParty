import { check } from 'k6';
import type { Options } from 'k6/options';
import { Shopper, STORE, uuid, type Answer } from './shopper.ts';

/**
 * T-E6-14 (RNF-16, RNF-22): through the production Caddy image, one client goes over the limit of each per-minute
 * route a visitor reaches, one route after another, and gets 429 with Retry-After in problem+json. Caddy overwrites
 * X-Forwarded-For, so the forged address the shopper sends is ignored and every request counts for one IP.
 *
 * Left out, with their tests in the backend (RateLimitPoliciesTest, BackOfficeRateLimitTest): the hourly policies
 * (registration and recovery, email correction, withdrawal), which would lock the IP out for an hour, and the ones
 * keyed by a signed-in user (back office, account data and deletion). The routes share some buckets (a search
 * counts for the catalog, a coupon for the cart, a payment retry for the tracking link), so each one is checked to
 * refuse within its capacity (and the refill), in an order where it is reached before the bucket it shares runs out.
 */

const VARIANT_ID = __ENV.VARIANT_ID ?? '';

export const options: Options = {
  insecureSkipTLSVerify: true,
  scenarios: {
    limits: { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '3m' },
  },
  thresholds: {
    checks: ['rate==1'],
  },
};

/**
 * Tokens come back gradually (120 a minute is one every half second), so the requests sent meanwhile may earn a few
 * more: as RateLimitPoliciesTest does, the 429 may come up to this many requests past the capacity.
 */
const REFILL_SLACK = 10;

/** Sends the request until the first 429, at most capacity + REFILL_SLACK + 1 times, and checks that 429. */
function overTheLimit(route: string, capacity: number, send: () => Answer): void {
  let passed = 0;
  let refusal: Answer | undefined;
  for (let attempt = 0; attempt <= capacity + REFILL_SLACK; attempt++) {
    const answer = send();
    if (answer.status === 429) {
      refusal = answer;
      break;
    }
    passed++;
  }
  check(refusal, {
    [`${route}: 429 past at most ${String(capacity)} requests and the refill`]: (a) => a !== undefined,
    [`${route}: Retry-After`]: (a) => Number(a?.headers['Retry-After'] ?? '0') >= 1,
    [`${route}: problem+json`]: (a) => (a?.headers['Content-Type'] ?? '').startsWith('application/problem+json'),
  });
}

export default function limits(): void {
  // A forged address: Caddy replaces it with the real one (RNF-22).
  const shopper = Shopper.at('203.0.113.7', STORE);
  shopper.get('/api/cart');
  const added = shopper.post('/api/cart/items', { variantId: VARIANT_ID, quantity: 1 });
  check(added, { 'the cart for the coupon and checkout limits': (a) => a.status === 200 });
  const token = 'A'.repeat(43);

  // Coupon (cart + IP, 10 a minute), before the cart's own 60 run out.
  overTheLimit('coupon', 10, () => shopper.post('/api/cart/coupon', { code: 'NO-EXISTE' }));
  // Checkout (cart + IP, 5 a minute); an incomplete body, so no order is ever placed.
  overTheLimit('checkout', 5, () =>
    shopper.post('/api/checkout', { acceptedTerms: false }, { 'Idempotency-Key': uuid() }),
  );
  // Cart changes (IP, 60 a minute): an unknown variant.
  overTheLimit('cart', 60, () => shopper.post('/api/cart/items', { variantId: uuid(), quantity: 1 }));

  // Payment retry (order + IP, 5 a minute), then the tracking link (IP, 30 a minute): an unknown token.
  overTheLimit('payment-retry', 5, () => shopper.post(`/api/orders/${token}/retry-payment`, {}));
  overTheLimit('order-tracking', 30, () => shopper.get(`/api/orders/${token}`));

  // Search (search + IP, 60 a minute), then the catalog (IP, 120 a minute), then the shipping costs (IP, 120).
  overTheLimit('search', 60, () => shopper.get('/api/catalog/suggestions?q=pijama'));
  overTheLimit('catalog', 120, () => shopper.get('/api/catalog/categories'));
  overTheLimit('purchase-info', 120, () => shopper.get('/api/shipping/rates'));

  // Sign-in (IP, 5 a minute), with a wrong password; the account is never told apart (RNF-29.5).
  overTheLimit('login', 5, () =>
    shopper.post('/api/account/login', { email: `nadie.${uuid()}@pijamaparty.test`, password: 'x' }),
  );
  // Returns from Google or Facebook (IP, 10 a minute), with a code no provider issued.
  overTheLimit('social-sign-in', 10, () => shopper.get('/api/account/oauth2/code/google?code=x&state=y'));

  // A refusal is not an error of the API: it is still up.
  const health = shopper.get('/api/health');
  check(health, { 'the API is up': (a) => a.status === 200 });
}
