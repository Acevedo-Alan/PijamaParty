import { join } from 'node:path';
import { defineConfig, devices } from '@playwright/test';
import { BACKEND_URL, MERCADOPAGO_STUB_PORT, STOREFRONT_URL } from './stubs/settings.ts';

/**
 * T-E6-01 (RNF-10): the critical flows end to end, against the real backend (E2eStoreApplication: a throwaway
 * PostgreSQL, the e2e profile) and the production build of the frontend (vite preview, /api proxied to the backend),
 * with MercadoPago and Resend replaced by local stubs (stubs/). One command: `npm test`.
 *
 * The tests run one at a time: they share one database and one clock, which the rejected-payment test moves forward.
 */
// The Maven wrapper by its full path: Windows does not look for commands in the working directory.
const mvnw = `"${join(import.meta.dirname, '..', 'backend', process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw')}"`;
const isCi = process.env.CI !== undefined;

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  forbidOnly: isCi,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: isCi ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: STOREFRONT_URL,
    locale: 'es-AR',
    timezoneId: 'America/Argentina/Buenos_Aires',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /.*\.setup\.ts/ },
    {
      name: 'mobile',
      // Mobile first (CLAUDE.md): a phone viewport on Chromium.
      use: { ...devices['Pixel 7'] },
      dependencies: ['setup'],
    },
  ],
  webServer: [
    {
      command: 'node stubs/server.ts',
      url: `http://127.0.0.1:${String(MERCADOPAGO_STUB_PORT)}/__e2e/health`,
      reuseExistingServer: !isCi,
      timeout: 30_000,
    },
    {
      command: `${mvnw} -q spring-boot:test-run -Dspring-boot.run.main-class=com.pijamaparty.store.E2eStoreApplication`,
      cwd: '../backend',
      url: `${BACKEND_URL}/api/health`,
      reuseExistingServer: !isCi,
      timeout: 600_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm run build && npm run preview -- --port 4173 --strictPort',
      cwd: '../frontend',
      url: STOREFRONT_URL,
      reuseExistingServer: !isCi,
      timeout: 300_000,
    },
  ],
});
