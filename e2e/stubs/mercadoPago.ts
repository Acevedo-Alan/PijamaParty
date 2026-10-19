import { createHmac, randomUUID } from 'node:crypto';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { escapeHtml, hasBearer, readBody, sendHtml, sendJson } from './http.ts';
import { MERCADOPAGO_ACCESS_TOKEN, MERCADOPAGO_CHECKOUT_ORIGIN, MERCADOPAGO_WEBHOOK_SECRET } from './settings.ts';

/**
 * T-E6-01: a stub of the part of MercadoPago the store uses, so the real adapters run unchanged
 * (MercadoPagoGateway, MercadoPagoPaymentVerifier, MercadoPagoSignature):
 * - POST /checkout/preferences creates the Checkout Pro preference (UC-14);
 * - GET /v1/payments/{id} shows a payment (UC-15);
 * - GET /checkout is the page where the buyer pays: approve or reject. Either sends the signed Webhooks notification
 *   to the preference's notification_url, as MercadoPago does; an approved payment then sends the buyer back to the
 *   store, and a rejected one stays on the page so the buyer can try again (T-E3-07).
 * The tests drive it through /__e2e: repeat a notification, list the payments of an order, and (for the load test,
 * T-E6-14) pay an order without notifying, getting back the signed notification for k6 to deliver.
 */

interface Preference {
  id: string;
  title: string;
  amount: number;
  externalReference: string;
  successUrl: string;
  notificationUrl: string;
}

interface Payment {
  id: number;
  status: 'approved' | 'rejected';
  externalReference: string;
  amount: number;
  preferenceId: string;
}

interface PreferenceBody {
  items?: { title?: string; unit_price?: number }[];
  external_reference?: string;
  back_urls?: { success?: string };
  notification_url?: string;
}

const preferences = new Map<string, Preference>();
const preferencesByIdempotencyKey = new Map<string, string>();
const payments = new Map<number, Payment>();
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
  } else if (method === 'POST' && url.pathname === '/checkout/preferences') {
    await createPreference(request, response);
  } else if (method === 'GET' && url.pathname.startsWith('/v1/payments/')) {
    showPayment(request, response, url.pathname.slice('/v1/payments/'.length));
  } else if (method === 'GET' && url.pathname === '/checkout') {
    const preference = preferences.get(url.searchParams.get('pref_id') ?? '');
    if (preference === undefined) {
      sendHtml(response, 404, '<h1>Unknown preference</h1>');
    } else {
      sendHtml(response, 200, checkoutPage(preference, null));
    }
  } else if (method === 'POST' && url.pathname === '/checkout/pay') {
    await pay(request, response);
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

async function createPreference(request: IncomingMessage, response: ServerResponse): Promise<void> {
  if (!hasBearer(request, MERCADOPAGO_ACCESS_TOKEN)) {
    sendJson(response, 401, { message: 'invalid access token' });
    return;
  }
  const idempotencyKey = request.headers['x-idempotency-key'];
  const body = JSON.parse(await readBody(request)) as PreferenceBody;
  const item = body.items?.[0];
  if (
    typeof idempotencyKey !== 'string' ||
    item?.unit_price === undefined ||
    body.external_reference === undefined ||
    body.back_urls?.success === undefined ||
    body.notification_url === undefined
  ) {
    sendJson(response, 400, { message: 'incomplete preference' });
    return;
  }
  // RNF-20.8: the same key gets the same preference, as MercadoPago does.
  const existing = preferencesByIdempotencyKey.get(idempotencyKey);
  const preference: Preference = existing === undefined
    ? {
        id: `e2e-${randomUUID()}`,
        title: item.title ?? '',
        amount: item.unit_price,
        externalReference: body.external_reference,
        successUrl: body.back_urls.success,
        notificationUrl: body.notification_url,
      }
    : (preferences.get(existing) as Preference);
  preferences.set(preference.id, preference);
  preferencesByIdempotencyKey.set(idempotencyKey, preference.id);
  sendJson(response, 201, {
    id: preference.id,
    init_point: `${MERCADOPAGO_CHECKOUT_ORIGIN}/checkout?pref_id=${encodeURIComponent(preference.id)}`,
  });
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
    external_reference: payment.externalReference,
    transaction_amount: payment.amount,
    currency_id: 'ARS',
  });
}

async function pay(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const form = new URLSearchParams(await readBody(request));
  const preference = preferences.get(form.get('pref_id') ?? '');
  const outcome = form.get('outcome');
  if (preference === undefined || (outcome !== 'approved' && outcome !== 'rejected')) {
    sendHtml(response, 400, '<h1>Invalid payment</h1>');
    return;
  }
  const payment: Payment = {
    id: nextPaymentId++,
    status: outcome,
    externalReference: preference.externalReference,
    amount: preference.amount,
    preferenceId: preference.id,
  };
  payments.set(payment.id, payment);
  await notify(payment);
  if (outcome === 'rejected') {
    sendHtml(response, 200, checkoutPage(preference, 'Payment rejected. Try another card.'));
    return;
  }
  // MercadoPago's auto_return: back to the store with the payment's result in the query string.
  const back = new URL(preference.successUrl);
  back.search = new URLSearchParams({
    collection_id: String(payment.id),
    collection_status: 'approved',
    payment_id: String(payment.id),
    status: 'approved',
    external_reference: payment.externalReference,
    preference_id: preference.id,
  }).toString();
  const target = escapeHtml(back.toString());
  sendHtml(
    response,
    200,
    `<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=${target}">` +
      `<title>Redirecting</title><a href="${target}">Back to the store</a>`,
  );
}

/** A payment's Webhooks notification, signed as MercadoPago signs it. */
interface Notification {
  url: URL;
  headers: Record<string, string>;
  body: string;
}

function notificationOf(payment: Payment): Notification {
  const preference = preferences.get(payment.preferenceId) as Preference;
  const dataId = String(payment.id);
  const requestId = randomUUID();
  const ts = String(Date.now());
  const manifest = `id:${dataId};request-id:${requestId};ts:${ts};`;
  const v1 = createHmac('sha256', MERCADOPAGO_WEBHOOK_SECRET).update(manifest).digest('hex');
  const url = new URL(preference.notificationUrl);
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
 * T-E6-14: approves a payment of the order's latest preference without notifying the store, and answers the signed
 * notification (path and query, headers and body), which the load test delivers several times at once.
 */
async function payQuietly(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const { externalReference } = JSON.parse(await readBody(request)) as { externalReference?: string };
  const preference = [...preferences.values()].findLast(
    (candidate) => candidate.externalReference === externalReference,
  );
  if (preference === undefined) {
    sendJson(response, 404, { message: 'unknown order' });
    return;
  }
  const payment: Payment = {
    id: nextPaymentId++,
    status: 'approved',
    externalReference: preference.externalReference,
    amount: preference.amount,
    preferenceId: preference.id,
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

function checkoutPage(preference: Preference, message: string | null): string {
  const amount = escapeHtml(preference.amount.toFixed(2));
  const id = escapeHtml(preference.id);
  const button = (outcome: string, label: string) =>
    `<form method="post" action="/checkout/pay"><input type="hidden" name="pref_id" value="${id}">` +
    `<input type="hidden" name="outcome" value="${outcome}"><button type="submit">${label}</button></form>`;
  return (
    `<!doctype html><html lang="en"><meta charset="utf-8"><title>MercadoPago (stub)</title>` +
    `<h1>MercadoPago (stub)</h1><p>${escapeHtml(preference.title)}</p>` +
    `<p>Amount: <output aria-label="Amount">${amount}</output></p>` +
    (message === null ? '' : `<p role="alert">${escapeHtml(message)}</p>`) +
    button('approved', 'Approve payment') +
    button('rejected', 'Reject payment') +
    `</html>`
  );
}
