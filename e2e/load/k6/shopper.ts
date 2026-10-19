import http, { CookieJar, type RefinedResponse, type RequestBody } from 'k6/http';
import { randomBytes } from 'k6/crypto';

/**
 * T-E6-14: what every load script shares. k6 runs in the network of the Caddy container (load/k6.ts), so the
 * backend is `api:8080`, as Caddy itself reaches it, and Caddy is `localhost`.
 */

/** The backend, directly: each shopper's own X-Forwarded-For is trusted (a private peer, the dev profile). */
export const API = __ENV.API_URL ?? 'http://api:8080';
/** The production Caddy image: it overwrites X-Forwarded-For, so every request comes from one IP (RNF-22). */
export const STORE = __ENV.STORE_URL ?? 'https://localhost';

/**
 * A shopper's own IP, from the benchmarking range 198.18.0.0/15 (RFC 2544), which no real client and no proxy rule
 * uses. Each script numbers its shoppers from its own base, so two runs a minute apart never share a bucket.
 */
export function shopperIp(n: number): string {
  return `198.18.${String(Math.floor(n / 256) % 256)}.${String(n % 256)}`;
}

export type Answer = RefinedResponse<'text'>;

export interface CookieState {
  ip: string;
  cookies: Record<string, string>;
}

/**
 * A browser of the store, seen by the API: its IP, its cookies and the CSRF token (RNF-29.1). The cookies are kept
 * by hand: the backend marks them Secure, and k6's own jar would not send them over plain http to the backend.
 */
export class Shopper {
  readonly ip: string;
  private readonly cookies: Record<string, string>;
  private readonly base: string;

  constructor(state: CookieState, base: string = API) {
    this.ip = state.ip;
    this.cookies = { ...state.cookies };
    this.base = base;
  }

  static at(ip: string, base: string = API): Shopper {
    return new Shopper({ ip, cookies: {} }, base);
  }

  /** The cookies, to hand a shopper made in setup() to a virtual user. */
  state(): CookieState {
    return { ip: this.ip, cookies: { ...this.cookies } };
  }

  get(path: string, tags: Record<string, string> = {}): Answer {
    return this.send('GET', path, null, {}, tags);
  }

  post(path: string, body: unknown, headers: Record<string, string> = {}, tags: Record<string, string> = {}): Answer {
    return this.send('POST', path, JSON.stringify(body), { 'Content-Type': 'application/json', ...headers }, tags);
  }

  /** The request as params of http.batch, for several requests at the same moment. */
  batchPost(path: string, body: unknown, headers: Record<string, string> = {}, tags: Record<string, string> = {}) {
    return {
      method: 'POST',
      url: `${this.base}${path}`,
      body: JSON.stringify(body),
      params: this.params({ 'Content-Type': 'application/json', ...headers }, tags),
    };
  }

  /** Reads the cookies an answer set; for answers of http.batch. */
  remember(answer: Answer): void {
    for (const [name, values] of Object.entries(answer.cookies)) {
      const last = values[values.length - 1];
      if (last !== undefined) {
        this.cookies[name] = last.value;
      }
    }
  }

  private send(
    method: string,
    path: string,
    body: RequestBody | null,
    headers: Record<string, string>,
    tags: Record<string, string>,
  ): Answer {
    const answer = http.request(method, `${this.base}${path}`, body, this.params(headers, tags));
    this.remember(answer);
    return answer;
  }

  private params(headers: Record<string, string>, tags: Record<string, string>) {
    const cookie = Object.entries(this.cookies)
      .map(([name, value]) => `${name}=${value}`)
      .join('; ');
    const csrf = this.cookies['XSRF-TOKEN'];
    return {
      headers: {
        'X-Forwarded-For': this.ip,
        ...(cookie === '' ? {} : { Cookie: cookie }),
        ...(csrf === undefined ? {} : { 'X-XSRF-TOKEN': decodeURIComponent(csrf) }),
        ...headers,
      },
      // A jar of its own per request: the cookies above are the only ones sent.
      jar: new CookieJar(),
      // An API answer is the answer: a redirect (the return from Google) points at the storefront, not followed.
      redirects: 0,
      tags,
      responseType: 'text' as const,
    };
  }
}

/** The problem code of a problem+json answer (the "problem" property), or undefined. */
export function problemOf(answer: Answer): string | undefined {
  try {
    const body = answer.json() as { problem?: string } | null;
    return body?.problem;
  } catch {
    return undefined;
  }
}

/** A guest buyer in Santa Fe; `name` tells the load test's orders apart in the back office. */
export function checkoutBody(name: string, email: string) {
  return {
    buyer: { name, email, phone: '3424567890', nationalId: '30123456' },
    address: { street: 'San Martín', number: '1234', city: 'Santa Fe', province: 'SANTA_FE', postalCode: '3000' },
    paymentProvider: 'MERCADOPAGO',
    acceptedTerms: true,
  };
}

/** A shopper with a cart holding one unit of the variant, ready to check out. */
export function shopperWithCart(ip: string, variantId: string): Shopper {
  const shopper = Shopper.at(ip);
  // Any answer sets the CSRF cookie that the writes need.
  shopper.get('/api/cart');
  const added = shopper.post('/api/cart/items', { variantId, quantity: 1 });
  if (added.status !== 200) {
    throw new Error(`Adding to the cart answered ${String(added.status)}: ${added.body}`);
  }
  return shopper;
}

/** A random UUID (version 4), as the storefront's crypto.randomUUID() writes it: for Idempotency-Key. */
export function uuid(): string {
  const bytes = new Uint8Array(randomBytes(16));
  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x40;
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
