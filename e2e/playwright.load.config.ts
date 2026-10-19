import { join } from 'node:path';
import { defineConfig } from '@playwright/test';
import { BACKEND_URL, CADDY_URL, MERCADOPAGO_STUB_PORT } from './stubs/settings.ts';

/**
 * T-E6-14 (RNF-16): the load test of the spec ("Prueba de carga (k6)"), with k6 scripts in TypeScript (load/k6), run
 * locally against the stack of the Lighthouse run: the production Caddy image (lighthouse/caddy.setup.ts) in front of
 * the e2e backend, with the MercadoPago and Resend stubs. Playwright seeds the catalog through the back office, runs
 * each k6 script and checks what the database kept (orders, stock, emails). One command: `npm run load`.
 *
 * Not in CI: shared runners make the latencies noisy. The figures that tune RNF-21 and RNF-22 come from the server.
 */
// The Maven wrapper by its full path: Windows does not look for commands in the working directory.
const mvnw = `"${join(import.meta.dirname, '..', 'backend', process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw')}"`;

export default defineConfig({
  testDir: './load',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 600_000,
  expect: { timeout: 30_000 },
  reporter: [['list']],
  globalSetup: './lighthouse/caddy.setup.ts',
  use: {
    baseURL: CADDY_URL,
    // Caddy's certificate for localhost comes from its own local authority.
    ignoreHTTPSErrors: true,
    locale: 'es-AR',
    timezoneId: 'America/Argentina/Buenos_Aires',
  },
  projects: [
    // The administrator's session, as in the end-to-end tests, to seed the catalog and read the orders.
    { name: 'setup', testDir: './tests', testMatch: /admin\.setup\.ts/ },
    { name: 'load', dependencies: ['setup'] },
  ],
  webServer: [
    {
      command: 'node stubs/server.ts',
      url: `http://127.0.0.1:${String(MERCADOPAGO_STUB_PORT)}/__e2e/health`,
      reuseExistingServer: true,
      timeout: 30_000,
    },
    {
      command: `${mvnw} -q spring-boot:test-run -Dspring-boot.run.main-class=com.pijamaparty.store.E2eStoreApplication`,
      cwd: '../backend',
      url: `${BACKEND_URL}/api/health`,
      reuseExistingServer: true,
      timeout: 600_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
});
