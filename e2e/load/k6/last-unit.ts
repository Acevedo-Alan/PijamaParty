import { check } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import type { Options } from 'k6/options';
import { checkoutBody, problemOf, Shopper, shopperWithCart, shopperIp, uuid, type CookieState } from './shopper.ts';

/**
 * T-E6-14 (RNF-16, RNF-20.4, UC-13): 20 buyers, each with the last unit of one variant in its cart, confirm at the
 * same moment. Exactly one gets the order; the others are refused with 409 and nothing breaks.
 */

const BUYERS = Number(__ENV.BUYERS ?? '20');
const VARIANT_ID = __ENV.VARIANT_ID ?? '';
const RUN = __ENV.RUN ?? 'local';
const IP_BASE = 3_000;

const placed = new Counter('orders_placed');
const refused = new Counter('orders_refused');

export const options: Options = {
  scenarios: {
    lastUnit: { executor: 'per-vu-iterations', vus: BUYERS, iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    orders_placed: ['count==1'],
    orders_refused: [`count==${String(BUYERS - 1)}`],
    checks: ['rate==1'],
  },
};

export function setup(): CookieState[] {
  return Array.from({ length: BUYERS }, (_, n) => shopperWithCart(shopperIp(IP_BASE + n + 1), VARIANT_ID).state());
}

export default function confirm(carts: CookieState[]): void {
  const n = exec.vu.idInTest;
  const shopper = new Shopper(carts[n - 1] as CookieState);
  const body = checkoutBody(`Carga ${RUN} última ${String(n)}`, `carga.${RUN}.ultima.${String(n)}@pijamaparty.test`);
  const answer = shopper.post('/api/checkout', body, { 'Idempotency-Key': uuid() }, { name: 'checkout' });

  // Whoever loses the race finds the cart no longer ready (CART_NOT_READY) or the stock gone (INSUFFICIENT_STOCK).
  const lost = answer.status === 409 && ['CART_NOT_READY', 'INSUFFICIENT_STOCK'].includes(problemOf(answer) ?? '');
  check(answer, { 'the order or the out-of-stock refusal': (a) => a.status === 201 || lost });
  if (answer.status === 201) {
    placed.add(1);
  } else if (lost) {
    refused.add(1);
  }
}
