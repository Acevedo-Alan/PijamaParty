import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import type { Options } from 'k6/options';
import {
  checkoutBody,
  problemOf,
  Shopper,
  shopperWithCart,
  shopperIp,
  uuid,
  type Answer,
  type CookieState,
} from './shopper.ts';

/**
 * T-E6-14 (RNF-16, RNF-20.1, RNF-20.2): 10 buyers confirm their checkout at the same moment, each from its own IP
 * and cart, and each sends its confirmation twice at once with the same Idempotency-Key, as a double tap or a retry
 * does. Every buyer gets exactly one order; the load test then checks that the stock was reserved once per order.
 */

const BUYERS = Number(__ENV.BUYERS ?? '10');
const VARIANT_ID = __ENV.VARIANT_ID ?? '';
const RUN = __ENV.RUN ?? 'local';
const IP_BASE = 2_000;

const placed = new Counter('orders_placed');

export const options: Options = {
  scenarios: {
    checkout: { executor: 'per-vu-iterations', vus: BUYERS, iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    orders_placed: [`count==${String(BUYERS)}`],
    checks: ['rate==1'],
  },
};

export function setup(): CookieState[] {
  return Array.from({ length: BUYERS }, (_, n) => shopperWithCart(shopperIp(IP_BASE + n + 1), VARIANT_ID).state());
}

function numberOf(answer: Answer): number | undefined {
  return answer.status === 201 ? (answer.json('number') as number) : undefined;
}

export default function checkout(carts: CookieState[]): void {
  const n = exec.vu.idInTest;
  const shopper = new Shopper(carts[n - 1] as CookieState);
  const key = uuid();
  const body = checkoutBody(`Carga ${RUN} ${String(n)}`, `carga.${RUN}.${String(n)}@pijamaparty.test`);
  const request = shopper.batchPost('/api/checkout', body, { 'Idempotency-Key': key }, { name: 'checkout' });

  const answers = http.batch([request, request]) as Answer[];
  answers.forEach((answer) => shopper.remember(answer));
  check(answers, {
    'each answer is the order or "key in use"': (all) =>
      all.every((a) => a.status === 201 || (a.status === 409 && problemOf(a) === 'IDEMPOTENCY_KEY_IN_USE')),
    'at least one answer is the order': (all) => all.some((a) => a.status === 201),
  });

  // A "key in use" is repeated after its Retry-After, as the storefront does, and must get the same order.
  const numbers = answers.map(numberOf);
  for (const answer of answers.filter((a) => a.status === 409)) {
    sleep(Number(answer.headers['Retry-After'] ?? '1'));
    const again = shopper.post('/api/checkout', body, { 'Idempotency-Key': key }, { name: 'checkout-retry' });
    check(again, { 'the repeated key gets the order': (a) => a.status === 201 });
    numbers.push(numberOf(again));
  }
  const distinct = new Set(numbers.filter((number) => number !== undefined));
  check(distinct, { 'one order for one key': (orders) => orders.size === 1 });
  placed.add(distinct.size);
}
