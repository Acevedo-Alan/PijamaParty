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
 * The MercadoPago checkout must be an https URL (the payments domain only accepts those), so the stub hands out
 * this host and the tests route it to the stub's own port (support/fixtures.ts). Nothing resolves it otherwise.
 */
export const MERCADOPAGO_CHECKOUT_ORIGIN = 'https://mercadopago.e2e.test';

export const ADMIN_EMAIL = 'admin@pijamaparty.test';
export const ADMIN_PASSWORD = 'e2e-admin-password';
