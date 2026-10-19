import { startMercadoPagoStub } from './mercadoPago.ts';
import { startResendStub } from './resend.ts';
import { MERCADOPAGO_STUB_PORT, RESEND_STUB_PORT } from './settings.ts';

/** T-E6-01: the payment and email provider stubs, started by Playwright (playwright.config.ts) or `npm run stubs`. */
const servers = [startMercadoPagoStub(MERCADOPAGO_STUB_PORT), startResendStub(RESEND_STUB_PORT)];
console.log(`Stubs listening: MercadoPago on ${String(MERCADOPAGO_STUB_PORT)}, Resend on ${String(RESEND_STUB_PORT)}`);

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    for (const server of servers) {
      server.close();
    }
    process.exit(0);
  });
}
