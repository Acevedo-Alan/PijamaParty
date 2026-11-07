import { join } from 'node:path';
import { defineConfig, devices } from '@playwright/test';
import { BACKEND_URL, MERCADOPAGO_STUB_PORT, STOREFRONT_URL } from './stubs/settings.ts';

/**
 * T-E6-19 (RNF-28): sqlmap against the routes with parameters (filters, search, cart, coupon, checkout, withdrawal,
 * sign-in and the back office's lists), run locally against the e2e backend with the MercadoPago and Resend stubs.
 * Playwright seeds the data through the back office and starts the session proxy that sqlmap talks to (security/).
 * One command: `npm run security`. Not in CI: it takes tens of minutes.
 *
 * The backend starts with the rate limits off (`store.rate-limit.enabled=false`): they are tested by the load test and
 * the integration tests, and here they would answer 429 to sqlmap's thousands of requests before the code under test
 * ran. It never reuses a running backend, which may have them on; stop the dev backend first.
 */
// The Maven wrapper by its full path: Windows does not look for commands in the working directory.
const mvnw = `"${join(import.meta.dirname, '..', 'backend', process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw')}"`;

export default defineConfig({
  testDir: './security',
  testMatch: /.*\.spec\.ts/,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 1_200_000,
  expect: { timeout: 30_000 },
  reporter: [['list']],
  use: {
    baseURL: STOREFRONT_URL,
    locale: 'es-AR',
    timezoneId: 'America/Argentina/Buenos_Aires',
  },
  projects: [
    // The administrator's session, as in the end-to-end tests, to seed the data and to test the back office's lists.
    { name: 'setup', testDir: './tests', testMatch: /admin\.setup\.ts/, use: { ...devices['Desktop Chrome'] } },
    { name: 'security', dependencies: ['setup'], use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: [
    {
      command: 'node stubs/server.ts',
      url: `http://127.0.0.1:${String(MERCADOPAGO_STUB_PORT)}/__e2e/health`,
      reuseExistingServer: true,
      timeout: 30_000,
    },
    {
      command:
        `${mvnw} -q spring-boot:test-run -Dspring-boot.run.main-class=com.pijamaparty.store.E2eStoreApplication ` +
        '-Dspring-boot.run.jvmArguments=-Dstore.rate-limit.enabled=false',
      cwd: '../backend',
      url: `${BACKEND_URL}/api/health`,
      reuseExistingServer: false,
      timeout: 600_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm run build && npm run preview -- --port 4173 --strictPort',
      cwd: '../frontend',
      url: STOREFRONT_URL,
      reuseExistingServer: true,
      timeout: 300_000,
    },
  ],
});
