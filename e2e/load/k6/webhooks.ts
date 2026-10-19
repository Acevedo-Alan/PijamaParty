import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import type { Options } from 'k6/options';
import { API } from './shopper.ts';

/**
 * T-E6-14 (RNF-16, RNF-20.3 to RNF-20.5, RNF-20.7): MercadoPago delivers the same payment notification several times
 * at the same moment, for several orders at once. Every copy is answered 200; the load test then checks that each
 * order was paid once, its stock deducted once and its confirmation email sent once.
 *
 * NOTIFICATIONS: the signed notifications of the MercadoPago stub (POST /__e2e/payments), one per order.
 */

interface Notification {
  path: string;
  headers: Record<string, string>;
  body: string;
}

const NOTIFICATIONS = JSON.parse(__ENV.NOTIFICATIONS ?? '[]') as Notification[];
const COPIES = Number(__ENV.COPIES ?? '5');

export const options: Options = {
  scenarios: {
    webhooks: { executor: 'per-vu-iterations', vus: Math.max(NOTIFICATIONS.length, 1), iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    checks: ['rate==1'],
  },
};

export default function deliver(): void {
  const notification = NOTIFICATIONS[exec.vu.idInTest - 1];
  if (notification === undefined) {
    return;
  }
  const copy = {
    method: 'POST',
    url: `${API}${notification.path}`,
    body: notification.body,
    // Webhooks are not rate limited (RNF-22), so the IP does not matter here.
    params: { headers: notification.headers, tags: { name: 'webhook' } },
  };
  const answers = http.batch(Array.from({ length: COPIES }, () => copy));
  check(answers, { 'every copy is answered 200': (all) => all.every((answer) => answer.status === 200) });
}
