import { createHmac, randomUUID } from 'node:crypto';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { hasBearer, readBody, sendJson } from './http.ts';
import { E2E_CARD_TOKENS, MERCADOPAGO_ACCESS_TOKEN, MERCADOPAGO_WEBHOOK_SECRET } from './settings.ts';

/**
 * T-E6-01, T-E3-31: a stub of the part of MercadoPago the store uses, so the real adapters run unchanged
 * (MercadoPagoCardCharges, MercadoPagoPaymentVerifier, MercadoPagoSignature):
 * - POST /v1/payments charges the token of a card typed in the card form (UC-14, T-E3-29). The fake card form of the
 *   tests (support/mercadoPagoSdk.ts) hands out a token per outcome: approved, or rejected for insufficient funds. Then,
 *   as MercadoPago does, the signed Webhooks notification goes to the payment's notification_url (UC-15). A repeated
 *   X-Idempotency-Key gets the payment its first request created, without charging again;
 * - GET /v1/payments/{id} shows a payment (UC-15).
 * The tests drive it through /__e2e: repeat a notification, list the payments of an order, and (for the load test,
 * T-E6-14) pay an order without notifying, getting back the signed notification for k6 to deliver.
 */

interface Payment {
  id: number;
  status: 'approved' | 'rejected';
  statusDetail: string;
  externalReference: string;
  amount: number;
  notificationUrl: string;
}

interface PaymentBody {
  transaction_amount?: number;
  token?: string;
  external_reference?: string;
  notification_url?: string;
  payer?: { email?: string; identification?: { number?: string } };
}

const payments = new Map<number, Payment>();
const paymentsByIdempotencyKey = new Map<string, number>();
// From the current time, so ids never repeat across restarts of the stub: a reused backend (reuseExistingServer)
// already processed the previous run's ids and would rightly ignore their notifications (T-E3-15).
let nextPaymentId = Date.now();

export function startMercadoPagoStub(port: number): Server {
  const server = createServer((request, response) => {
    handle(request, response).catch((error: unknown) => {
      console.error('MercadoPago stub failed:', error);
      if (!response.headersSent) {
        sendJson(response, 500, { message: 'stub error' });
      }
    });
  });
  server.listen(port, '127.0.0.1');
  return server;
}

async function handle(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const url = new URL(request.url ?? '/', 'http://stub');
  const method = request.method ?? 'GET';

  if (method === 'GET' && url.pathname === '/__e2e/health') {
    sendJson(response, 200, { status: 'UP' });
  } else if (method === 'POST' && url.pathname === '/v1/payments') {
    await charge(request, response);
  } else if (method === 'GET' && url.pathname.startsWith('/v1/payments/')) {
    showPayment(request, response, url.pathname.slice('/v1/payments/'.length));
  } else if (method === 'GET' && url.pathname === '/__e2e/payments') {
    const order = url.searchParams.get('externalReference');
    sendJson(response, 200, [...payments.values()].filter((payment) => payment.externalReference === order));
  } else if (method === 'POST' && url.pathname === '/__e2e/payments') {
    await payQuietly(request, response);
  } else if (method === 'POST' && /^\/__e2e\/payments\/\d+\/notify$/.test(url.pathname)) {
    const payment = payments.get(Number(url.pathname.split('/')[3]));
    if (payment === undefined) {
      sendJson(response, 404, { message: 'unknown payment' });
    } else {
      sendJson(response, 200, { status: await notify(payment) });
    }
  } else {
    sendJson(response, 404, { message: 'not found' });
  }
}

/** The answer MercadoPago gives to a created payment: its id, status and why. */
function chargeAnswer(payment: Payment): unknown {
  return { id: payment.id, status: payment.status, status_detail: payment.statusDetail };
}

async function charge(request: IncomingMessage, response: ServerResponse): Promise<void> {
  if (!hasBearer(request, MERCADOPAGO_ACCESS_TOKEN)) {
    sendJson(response, 401, { message: 'invalid access token' });
    return;
  }
  const idempotencyKey = request.headers['x-idempotency-key'];
  const body = JSON.parse(await readBody(request)) as PaymentBody;
  if (
    typeof idempotencyKey !== 'string' ||
    body.transaction_amount === undefined ||
    body.external_reference === undefined ||
    body.notification_url === undefined ||
    body.payer?.identification?.number === undefined
  ) {
    sendJson(response, 400, { message: 'incomplete payment' });
    return;
  }
  // RNF-20: the same key gets the same payment, charged once, as MercadoPago does.
  const existing = paymentsByIdempotencyKey.get(idempotencyKey);
  if (existing !== undefined) {
    sendJson(response, 201, chargeAnswer(payments.get(existing) as Payment));
    return;
  }
  const approved = body.token === E2E_CARD_TOKENS.approved;
  if (!approved && body.token !== E2E_CARD_TOKENS.withoutFunds) {
    sendJson(response, 400, { message: 'Card token not found', status: 400 });
    return;
  }
  const payment: Payment = {
    id: nextPaymentId++,
    status: approved ? 'approved' : 'rejected',
    statusDetail: approved ? 'accredited' : 'cc_rejected_insufficient_amount',
    externalReference: body.external_reference,
    amount: body.transaction_amount,
    notificationUrl: body.notification_url,
  };
  payments.set(payment.id, payment);
  paymentsByIdempotencyKey.set(idempotencyKey, payment.id);
  sendJson(response, 201, chargeAnswer(payment));
  // MercadoPago notifies on its own, a moment after answering.
  setTimeout(() => {
    notify(payment).catch((error: unknown) => {
      console.error('MercadoPago stub could not notify:', error);
    });
  }, 100);
}

function showPayment(request: IncomingMessage, response: ServerResponse, id: string): void {
  if (!hasBearer(request, MERCADOPAGO_ACCESS_TOKEN)) {
    sendJson(response, 401, { message: 'invalid access token' });
    return;
  }
  const payment = payments.get(Number(id));
  if (payment === undefined) {
    sendJson(response, 404, { message: 'Payment not found' });
    return;
  }
  sendJson(response, 200, {
    id: payment.id,
    status: payment.status,
    status_detail: payment.statusDetail,
    external_reference: payment.externalReference,
    transaction_amount: payment.amount,
    currency_id: 'ARS',
  });
}

/** A payment's Webhooks notification, signed as MercadoPago signs it. */
interface Notification {
  url: URL;
  headers: Record<string, string>;
  body: string;
}

function notificationOf(payment: Payment): Notification {
  const dataId = String(payment.id);
  const requestId = randomUUID();
  const ts = String(Date.now());
  const manifest = `id:${dataId};request-id:${requestId};ts:${ts};`;
  const v1 = createHmac('sha256', MERCADOPAGO_WEBHOOK_SECRET).update(manifest).digest('hex');
  const url = new URL(payment.notificationUrl);
  url.searchParams.set('type', 'payment');
  url.searchParams.set('data.id', dataId);
  return {
    url,
    headers: { 'Content-Type': 'application/json', 'x-signature': `ts=${ts},v1=${v1}`, 'x-request-id': requestId },
    body: JSON.stringify({ action: 'payment.updated', type: 'payment', data: { id: dataId } }),
  };
}

/** Sends the payment's Webhooks notification and returns the store's status. */
async function notify(payment: Payment): Promise<number> {
  const notification = notificationOf(payment);
  const answer = await fetch(notification.url, {
    method: 'POST',
    headers: notification.headers,
    body: notification.body,
  });
  return answer.status;
}

/**
 * T-E6-14: approves a payment of the order for its amount without notifying the store, and answers the signed
 * notification (path and query, headers and body), which the load test delivers several times at once.
 */
async function payQuietly(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const { externalReference, amount, notificationUrl } = JSON.parse(await readBody(request)) as {
    externalReference?: string;
    amount?: number;
    notificationUrl?: string;
  };
  if (externalReference === undefined || amount === undefined || notificationUrl === undefined) {
    sendJson(response, 400, { message: 'externalReference, amount and notificationUrl are required' });
    return;
  }
  const payment: Payment = {
    id: nextPaymentId++,
    status: 'approved',
    statusDetail: 'accredited',
    externalReference,
    amount,
    notificationUrl,
  };
  payments.set(payment.id, payment);
  const notification = notificationOf(payment);
  sendJson(response, 201, {
    paymentId: payment.id,
    path: `${notification.url.pathname}${notification.url.search}`,
    headers: notification.headers,
    body: notification.body,
  });
}
