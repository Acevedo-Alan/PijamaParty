/**
 * The values the stubs, the tests and the backend's e2e profile share
 * (backend/src/test/resources/config/application-e2e.yml). Test-only credentials: nothing here is a real secret.
 */
export const STOREFRONT_URL = 'http://localhost:4173';
/** T-E6-02: the production Caddy image in front of the same backend, for the Lighthouse run (lighthouse/). */
export const CADDY_HTTPS_PORT = 8443;
export const CADDY_URL = `https://localhost:${String(CADDY_HTTPS_PORT)}`;
export const BACKEND_URL = 'http://localhost:8080';
export const MERCADOPAGO_STUB_PORT = 8091;
export const RESEND_STUB_PORT = 8092;
export const CLOCK_CONTROL_URL = 'http://127.0.0.1:8093';

export const MERCADOPAGO_ACCESS_TOKEN = 'e2e-access-token';
export const MERCADOPAGO_WEBHOOK_SECRET = 'e2e-webhook-secret';
export const RESEND_API_KEY = 'e2e-resend-key';

/**
 * T-E3-31: the card tokens the fake card form (support/mercadoPagoSdk.ts) hands out, one per outcome the MercadoPago
 * stub knows: approved, and rejected for insufficient funds.
 */
export const E2E_CARD_TOKENS = { approved: 'e2e-card-approved', withoutFunds: 'e2e-card-without-funds' } as const;

export const ADMIN_EMAIL = 'admin@pijamaparty.test';
export const ADMIN_PASSWORD = 'e2e-admin-password';
