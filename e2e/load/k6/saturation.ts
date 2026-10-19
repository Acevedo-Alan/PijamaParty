import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';
import type { Options } from 'k6/options';
import { API, Shopper, shopperIp, STORE, type Answer } from './shopper.ts';

/**
 * T-E6-14 (RNF-16, RNF-21): far more shoppers than the pools hold ask for listings without pause, each from its own
 * IP so no rate limit stops them first, straight to the API. Once Tomcat's queue is full the API refuses new
 * connections; meanwhile shoppers through Caddy get that refusal (or a request that waited too long) as 503 with
 * Retry-After in problem+json, and their own IP's limit as 429. Nothing else may come back, and once the flood is
 * over the API must serve normally again.
 *
 * A 503 is checked whenever one comes, not required: on a developer machine the listings are too fast to exhaust the
 * connection pool, and Caddy reuses its open connections to the API, so the overload shows as refused direct
 * connections instead (T-E6-14, "As built"). The server run reads saturated_503 and refused_connections.
 */

const SHOPPERS = Number(__ENV.SHOPPERS ?? '300');
const THROUGH_CADDY = Number(__ENV.THROUGH_CADDY ?? '50');
const DURATION = __ENV.DURATION ?? '30s';
/** Requests already queued in Tomcat when the flood stops still get their answer; they may wait this long. */
const DRAIN = '45s';
const IP_BASE = 10_000;

const unexpected = new Rate('unexpected_answers');
const saturated = new Counter('saturated_503');
const limited = new Counter('limited_429');
const refusedConnections = new Counter('refused_connections');

export const options: Options = {
  insecureSkipTLSVerify: true,
  scenarios: {
    flood: { executor: 'constant-vus', vus: SHOPPERS, duration: DURATION, gracefulStop: DRAIN, exec: 'flood' },
    throughCaddy: {
      executor: 'constant-vus', vus: THROUGH_CADDY, duration: DURATION, gracefulStop: DRAIN, exec: 'throughCaddy',
    },
    // Once the flood has drained, a single shopper must be served as usual.
    after: { executor: 'per-vu-iterations', vus: 1, iterations: 1, startTime: DURATION, exec: 'after', maxDuration: '2m' },
  },
  thresholds: {
    unexpected_answers: ['rate==0'],
    checks: ['rate==1'],
  },
};

function listing(): string {
  return `/api/catalog/products?sort=PRICE_DESC&page=${String(exec.vu.iterationInScenario % 3)}`;
}

function checkUnavailable(answer: Answer): void {
  saturated.add(1);
  check(answer, {
    '503 carries Retry-After': (a) => Number(a.headers['Retry-After'] ?? '0') >= 1,
    '503 is problem+json': (a) => (a.headers['Content-Type'] ?? '').startsWith('application/problem+json'),
  });
}

export function flood(): void {
  const answer = Shopper.at(shopperIp(IP_BASE + exec.vu.idInTest)).get(listing(), { name: 'flood' });
  if (answer.status === 0) {
    // Tomcat's queue is full (RNF-21): Caddy answers this refusal with 503, as the Caddy shoppers see.
    refusedConnections.add(1);
    unexpected.add(false);
    return;
  }
  if (answer.status === 503) {
    checkUnavailable(answer);
  }
  unexpected.add(answer.status !== 200 && answer.status !== 503);
}

export function throughCaddy(): void {
  const answer = Shopper.at(shopperIp(IP_BASE), STORE).get(listing(), { name: 'flood-caddy' });
  if (answer.status === 503) {
    checkUnavailable(answer);
  } else if (answer.status === 429) {
    limited.add(1);
  }
  unexpected.add(![200, 429, 503].includes(answer.status));
}

export function after(): void {
  // The flood's queued requests drain first; then the API must be back within half a minute.
  sleep(Number.parseInt(DRAIN, 10));
  let health = http.get(`${API}/api/health`, { tags: { name: 'health' } });
  for (let attempt = 0; attempt < 30 && health.status !== 200; attempt++) {
    sleep(1);
    health = http.get(`${API}/api/health`, { tags: { name: 'health' } });
  }
  check(health, { 'the API is up after the flood': (a) => a.status === 200 });
  const served = Shopper.at(shopperIp(IP_BASE - 1)).get('/api/catalog/products', { name: 'after' });
  check(served, {
    'a listing is served after the flood': (a) => a.status === 200,
    'quickly (under 1 s)': (a) => a.timings.duration < 1_000,
  });
}
