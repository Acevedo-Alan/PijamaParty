import { randomUUID } from 'node:crypto';
import { createServer, request, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';

/**
 * T-E6-19: the HTTP proxy between sqlmap and the backend. sqlmap sends only the values it tests; the proxy puts back
 * what the API needs around them, so each payload reaches the code behind the validation instead of dying at the CSRF
 * check, the missing cart or the repeated Idempotency-Key:
 *
 * - every request carries the CSRF cookie and header of a guest session (RNF-29.1), and the guest's cart cookie;
 * - `/api/admin/*` (but the sign-in) carries the administrator's session instead;
 * - `/api/checkout` gets a new Idempotency-Key each time, and the cart is filled again after an order is placed.
 *
 * It listens on every interface because sqlmap runs in a container and reaches the host by `host.docker.internal`.
 */
export interface AdministratorSession {
  /** The `Cookie` header value of the signed-in back office. */
  cookie: string;
  csrfToken: string;
}

export interface SessionProxyOptions {
  backend: string;
  administrator: AdministratorSession;
  /** A variant the guest's cart is kept filled with: the checkout needs a cart with items. */
  cartVariantId: string;
}

const GUEST_CSRF = randomUUID();
const CART_COOKIE = '__Host-cart_token';
/** Headers that describe one connection, not the message; the proxy sets its own. */
const HOP_HEADERS = new Set(['connection', 'keep-alive', 'transfer-encoding', 'content-length', 'host', 'upgrade']);

export class SessionProxy {
  private readonly options: SessionProxyOptions;
  private readonly server: Server;
  private cartToken: string | undefined;

  constructor(options: SessionProxyOptions) {
    this.options = options;
    this.server = createServer((incoming, outgoing) => {
      void this.forward(incoming, outgoing);
    });
  }

  /** Fills the guest's cart, starts listening and returns the port. */
  async start(): Promise<number> {
    await this.fillCart();
    await new Promise<void>((resolve) => this.server.listen(0, '0.0.0.0', resolve));
    return (this.server.address() as AddressInfo).port;
  }

  stop(): Promise<void> {
    return new Promise((resolve) => this.server.close(() => resolve()));
  }

  private async fillCart(): Promise<void> {
    const response = await this.send('POST', '/api/cart/items', guestHeaders(this.cartToken, 'application/json'), {
      variantId: this.options.cartVariantId,
      quantity: 1,
    });
    if (response.status !== 200 && response.status !== 201) {
      throw new Error(`Filling the guest's cart answered ${String(response.status)}: ${response.body}`);
    }
    const issued = response.setCookies.find((cookie) => cookie.startsWith(`${CART_COOKIE}=`));
    if (issued !== undefined) {
      this.cartToken = issued.slice(CART_COOKIE.length + 1).split(';')[0];
    }
  }

  private async forward(incoming: IncomingMessage, outgoing: ServerResponse): Promise<void> {
    try {
      const path = incoming.url ?? '/';
      const method = incoming.method ?? 'GET';
      const body = await readBody(incoming);
      const isAdmin = path.startsWith('/api/admin/') && !path.startsWith('/api/admin/auth/login');
      const headers: Record<string, string> = isAdmin
        ? { Cookie: this.options.administrator.cookie, 'X-XSRF-TOKEN': this.options.administrator.csrfToken }
        : guestHeaders(this.cartToken);
      for (const [name, value] of Object.entries(incoming.headers)) {
        if (!HOP_HEADERS.has(name) && name !== 'cookie' && name !== 'x-xsrf-token' && typeof value === 'string') {
          headers[name] = value;
        }
      }
      if (method === 'POST' && path.split('?')[0] === '/api/checkout') {
        headers['Idempotency-Key'] = randomUUID();
      }
      const answer = await this.send(method, path, headers, body);
      if (method === 'POST' && path.split('?')[0] === '/api/checkout' && answer.status === 201) {
        await this.fillCart();
      }
      outgoing.writeHead(answer.status, answer.headers);
      outgoing.end(answer.body);
    } catch (error) {
      outgoing.writeHead(502, { 'Content-Type': 'text/plain' });
      outgoing.end(`proxy error: ${String(error)}`);
    }
  }

  private send(
    method: string,
    path: string,
    headers: Record<string, string>,
    body?: unknown,
  ): Promise<{ status: number; headers: Record<string, string>; body: string; setCookies: string[] }> {
    const payload = body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body);
    const url = new URL(path, this.options.backend);
    return new Promise((resolve, reject) => {
      const outgoing = request(
        url,
        { method, headers: { ...headers, ...(payload === undefined ? {} : { 'Content-Length': Buffer.byteLength(payload) }) } },
        (response) => {
          const chunks: Buffer[] = [];
          response.on('data', (chunk: Buffer) => chunks.push(chunk));
          response.on('end', () => {
            const kept: Record<string, string> = {};
            for (const [name, value] of Object.entries(response.headers)) {
              if (!HOP_HEADERS.has(name) && name !== 'set-cookie' && typeof value === 'string') {
                kept[name] = value;
              }
            }
            resolve({
              status: response.statusCode ?? 502,
              headers: kept,
              body: Buffer.concat(chunks).toString('utf8'),
              setCookies: response.headers['set-cookie'] ?? [],
            });
          });
        },
      );
      outgoing.on('error', reject);
      outgoing.end(payload);
    });
  }
}

function guestHeaders(cartToken: string | undefined, contentType?: string): Record<string, string> {
  return {
    Cookie: [`XSRF-TOKEN=${GUEST_CSRF}`, cartToken === undefined ? '' : `${CART_COOKIE}=${cartToken}`]
      .filter((cookie) => cookie !== '')
      .join('; '),
    'X-XSRF-TOKEN': GUEST_CSRF,
    ...(contentType === undefined ? {} : { 'Content-Type': contentType }),
  };
}

function readBody(incoming: IncomingMessage): Promise<string | undefined> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    incoming.on('data', (chunk: Buffer) => chunks.push(chunk));
    incoming.on('end', () => {
      const text = Buffer.concat(chunks).toString('utf8');
      resolve(text === '' ? undefined : text);
    });
    incoming.on('error', reject);
  });
}
