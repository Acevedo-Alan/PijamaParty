import { CLOCK_CONTROL_URL, MERCADOPAGO_STUB_PORT, RESEND_STUB_PORT } from '../stubs/settings.ts';

/**
 * What the tests read from or do to the test doubles, never to the store itself: the backend's adjustable clock
 * (E2eConfiguration) and the provider stubs.
 */

const MERCADOPAGO_STUB = `http://127.0.0.1:${String(MERCADOPAGO_STUB_PORT)}`;
const RESEND_STUB = `http://127.0.0.1:${String(RESEND_STUB_PORT)}`;

async function ok(response: Response): Promise<Response> {
  if (!response.ok) {
    throw new Error(`${response.url} answered ${String(response.status)}: ${await response.text()}`);
  }
  return response;
}

/** Moves the clock the application reads forward. */
export async function advanceClock(minutes: number): Promise<void> {
  await ok(await fetch(`${CLOCK_CONTROL_URL}/clock/advance?minutes=${String(minutes)}`, { method: 'POST' }));
}

/** Brings the clock back to the system time. */
export async function resetClock(): Promise<void> {
  await ok(await fetch(`${CLOCK_CONTROL_URL}/clock/reset`, { method: 'POST' }));
}

export interface SentEmail {
  id: string;
  to: string;
  subject: string;
}

/** The emails the store sent to one address through the Resend stub. */
export async function emailsTo(address: string): Promise<SentEmail[]> {
  const response = await ok(await fetch(`${RESEND_STUB}/__e2e/emails?to=${encodeURIComponent(address)}`));
  return (await response.json()) as SentEmail[];
}

export interface StubPayment {
  id: number;
  status: 'approved' | 'rejected';
  externalReference: string;
  amount: number;
}

/** The payments made at the MercadoPago stub for one order number. */
export async function paymentsOf(orderNumber: number): Promise<StubPayment[]> {
  const response = await ok(
    await fetch(`${MERCADOPAGO_STUB}/__e2e/payments?externalReference=${String(orderNumber)}`),
  );
  return (await response.json()) as StubPayment[];
}

/** Delivers a payment's Webhooks notification once more, as MercadoPago does; returns the store's HTTP status. */
export async function notifyAgain(paymentId: number): Promise<number> {
  const response = await ok(
    await fetch(`${MERCADOPAGO_STUB}/__e2e/payments/${String(paymentId)}/notify`, { method: 'POST' }),
  );
  return ((await response.json()) as { status: number }).status;
}
