import { createHmac, randomUUID } from 'node:crypto';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { hasBearer, readBody, sendJson } from './http.ts';
import {
  E2E_CARD_TOKENS,
  MERCADOPAGO_ACCESS_TOKEN,
  MERCADOPAGO_WEBHOOK_SECRET,
  MERCADOPAGO_WEBHOOK_URL,
} from './settings.ts';

/**
 * T-E6-01, T-E3-31, T-E3-34: a stub of the part of MercadoPago's Orders API the store uses, answering as the sandbox
 * did on 2026-11-17, so the real adapters run unchanged (MercadoPagoCardCharges, MercadoPagoPaymentVerifier,
 * MercadoPagoSignature):
 * - POST /v1/orders charges the token of a card typed in the card form (UC-14). The fake card form of the tests
 *   (support/mercadoPagoSdk.ts) hands out a token per outcome: approved (201, processed), or refused for insufficient
 *   funds (402, the failed order in `data`). Then, as MercadoPago does, the signed Webhooks notification of the order
 *   (`type=order`) goes to the application's Webhooks URL (UC-15). A repeated X-Idempotency-Key gets the order its
 *   first request created, without charging again;
 * - GET /v1/orders/{id} shows an order (UC-15); 400 for an id it does not know, as the Orders API answers.
 * The tests drive it through /__e2e: repeat a notification, list the orders of a store order, and (for the load test,
 * T-E6-14) pay a store order without notifying, getting back the signed notification for k6 to deliver.
 */

interface Order {
  id: string;
  status: 'approved' | 'rejected';
  externalReference: string;
  /** Pesos with two decimals, as the Orders API writes them. */
  amount: string;
}

interface OrderBody {
  total_amount?: string;
  external_reference?: string;
  payer?: { email?: string; identification?: { number?: string } };
  transactions?: { payments?: { amount?: string; payment_method?: { token?: string; type?: string } }[] };
}

const orders = new Map<string, Order>();
const ordersByIdempotencyKey = new Map<string, string>();
// From the current time, so ids never repeat across restarts of the stub: a reused backend (reuseExistingServer)
// already processed the previous run's ids and would rightly ignore their notifications (T-E3-15).
let nextOrder = Date.now();

function newOrderId(): string {
  return `ORDE2E01${String(nextOrder++)}`;
}

export function startMercadoPagoStub(port: number): Server {
  const server = createServer((request, response) => {
    handle(request, response).catch((error: unknown) => {
      console.error('MercadoPago stub failed:', error);
      if (!response.headersSent) {
        sendJson(response, 500, { errors: [{ code: 'stub_error' }] });
      }
    });
  });
  server.listen(port, '127.0.0.1');
  return server;
}

const NOTIFY = /^\/__e2e\/payments\/(ORD[A-Z0-9]+)\/notify$/;

async function handle(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const url = new URL(request.url ?? '/', 'http://stub');
  const method = request.method ?? 'GET';
  const notify = NOTIFY.exec(url.pathname);

  if (method === 'GET' && url.pathname === '/__e2e/health') {
    sendJson(response, 200, { status: 'UP' });
  } else if (method === 'POST' && url.pathname === '/v1/orders') {
    await charge(request, response);
  } else if (method === 'GET' && url.pathname.startsWith('/v1/orders/')) {
    showOrder(request, response, url.pathname.slice('/v1/orders/'.length));
  } else if (method === 'GET' && url.pathname === '/__e2e/payments') {
    const reference = url.searchParams.get('externalReference');
    sendJson(response, 200, [...orders.values()].filter((order) => order.externalReference === reference));
  } else if (method === 'POST' && url.pathname === '/__e2e/payments') {
    await payQuietly(request, response);
  } else if (method === 'POST' && notify !== null) {
    const order = orders.get(notify[1]!);
    if (order === undefined) {
      sendJson(response, 404, { message: 'unknown order' });
    } else {
      sendJson(response, 200, { status: await deliver(order) });
    }
  } else {
    sendJson(response, 404, { errors: [{ code: 'not_found' }] });
  }
}

/** The order as the Orders API writes it: processed and accredited, or failed for insufficient funds. */
function orderJson(order: Order): unknown {
  const approved = order.status === 'approved';
  return {
    id: order.id,
    type: 'online',
    processing_mode: 'automatic',
    external_reference: order.externalReference,
    total_amount: order.amount,
    total_paid_amount: approved ? order.amount : '0.00',
    currency: 'ARS',
    status: approved ? 'processed' : 'failed',
    status_detail: approved ? 'accredited' : 'failed',
    transactions: {
      payments: [
        {
          id: `PAY${order.id.slice(3)}`,
          amount: order.amount,
          status: approved ? 'processed' : 'failed',
          status_detail: approved ? 'accredited' : 'insufficient_amount',
        },
      ],
    },
  };
}

/** 201 with an approved order; 402 with the failed one in `data`, as the Orders API refuses a card. */
function chargeAnswer(response: ServerResponse, order: Order): void {
  if (order.status === 'approved') {
    sendJson(response, 201, orderJson(order));
  } else {
    sendJson(response, 402, {
      errors: [{ code: 'failed', message: 'The following transactions failed' }],
      data: orderJson(order),
    });
  }
}

async function charge(request: IncomingMessage, response: ServerResponse): Promise<void> {
  if (!hasBearer(request, MERCADOPAGO_ACCESS_TOKEN)) {
    sendJson(response, 401, { errors: [{ code: 'unauthorized', message: 'invalid access token' }] });
    return;
  }
  const idempotencyKey = request.headers['x-idempotency-key'];
  const body = JSON.parse(await readBody(request)) as OrderBody;
  const payment = body.transactions?.payments?.[0];
  if (
    typeof idempotencyKey !== 'string' ||
    body.total_amount === undefined ||
    body.external_reference === undefined ||
    body.payer?.identification?.number === undefined ||
    payment?.amount !== body.total_amount ||
    (payment.payment_method?.type !== 'credit_card' && payment.payment_method?.type !== 'debit_card')
  ) {
    sendJson(response, 400, { errors: [{ code: 'invalid_properties', message: 'incomplete order' }] });
    return;
  }
  // RNF-20: the same key gets the same order, charged once, as MercadoPago does.
  const existing = ordersByIdempotencyKey.get(idempotencyKey);
  if (existing !== undefined) {
    chargeAnswer(response, orders.get(existing)!);
    return;
  }
  const token = payment.payment_method.token;
  const approved = token === E2E_CARD_TOKENS.approved;
  if (!approved && token !== E2E_CARD_TOKENS.withoutFunds) {
    sendJson(response, 400, { errors: [{ code: 'invalid_card_token', message: 'Card token not found' }] });
    return;
  }
  const order: Order = {
    id: newOrderId(),
    status: approved ? 'approved' : 'rejected',
    externalReference: body.external_reference,
    amount: body.total_amount,
  };
  orders.set(order.id, order);
  ordersByIdempotencyKey.set(idempotencyKey, order.id);
  chargeAnswer(response, order);
  // MercadoPago notifies on its own, a moment after answering.
  setTimeout(() => {
    deliver(order).catch((error: unknown) => {
      console.error('MercadoPago stub could not notify:', error);
    });
  }, 100);
}

function showOrder(request: IncomingMessage, response: ServerResponse, id: string): void {
  if (!hasBearer(request, MERCADOPAGO_ACCESS_TOKEN)) {
    sendJson(response, 401, { errors: [{ code: 'unauthorized', message: 'invalid access token' }] });
    return;
  }
  const order = orders.get(id);
  if (order === undefined) {
    sendJson(response, 400, { errors: [{ code: 'invalid_path_param', message: 'order_id is invalid' }] });
    return;
  }
  sendJson(response, 200, orderJson(order));
}

/** An order's Webhooks notification, signed as MercadoPago signs it. */
interface Notification {
  url: URL;
  headers: Record<string, string>;
  body: string;
}

function notificationOf(order: Order): Notification {
  const requestId = randomUUID();
  const ts = String(Date.now());
  // MercadoPago signs an id with letters in lowercase (T-E3-34).
  const manifest = `id:${order.id.toLowerCase()};request-id:${requestId};ts:${ts};`;
  const v1 = createHmac('sha256', MERCADOPAGO_WEBHOOK_SECRET).update(manifest).digest('hex');
  const url = new URL(MERCADOPAGO_WEBHOOK_URL);
  url.searchParams.set('data.id', order.id);
  url.searchParams.set('type', 'order');
  return {
    url,
    headers: { 'Content-Type': 'application/json', 'x-signature': `ts=${ts},v1=${v1}`, 'x-request-id': requestId },
    body: JSON.stringify({ action: 'order.processed', type: 'order', data: { id: order.id } }),
  };
}

/** Sends the order's Webhooks notification and returns the store's status. */
async function deliver(order: Order): Promise<number> {
  const notification = notificationOf(order);
  const answer = await fetch(notification.url, {
    method: 'POST',
    headers: notification.headers,
    body: notification.body,
  });
  return answer.status;
}

/**
 * T-E6-14: approves an order of the store order for its amount without notifying the store, and answers the signed
 * notification (path and query, headers and body), which the load test delivers several times at once.
 */
async function payQuietly(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const { externalReference, amount } = JSON.parse(await readBody(request)) as {
    externalReference?: string;
    amount?: number;
  };
  if (externalReference === undefined || amount === undefined) {
    sendJson(response, 400, { message: 'externalReference and amount are required' });
    return;
  }
  const order: Order = { id: newOrderId(), status: 'approved', externalReference, amount: amount.toFixed(2) };
  orders.set(order.id, order);
  const notification = notificationOf(order);
  sendJson(response, 201, {
    paymentId: order.id,
    path: `${notification.url.pathname}${notification.url.search}`,
    headers: notification.headers,
    body: notification.body,
  });
}
