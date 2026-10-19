import { join } from 'node:path';
import { defineConfig, devices } from '@playwright/test';
import { BACKEND_URL, CADDY_URL } from './stubs/settings.ts';

/**
 * T-E6-02 (RNF-11): Lighthouse in mobile mode on the home page, a listing, a product page and the checkout, against
 * the production Caddy image (lighthouse/caddy.setup.ts) in front of the e2e backend. One command:
 * `npm run lighthouse`. The HTML reports go to lighthouse-report/.
 *
 * Needs libvips for the product photos (vips on the PATH, or VIPS_BIN_DIR), as the backend's image adapter does.
 */
// The Maven wrapper by its full path: Windows does not look for commands in the working directory.
const mvnw = `"${join(import.meta.dirname, '..', 'backend', process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw')}"`;
const isCi = process.env.CI !== undefined;

export default defineConfig({
  testDir: './lighthouse',
  fullyParallel: false,
  workers: 1,
  forbidOnly: isCi,
  retries: 0,
  timeout: 300_000,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  globalSetup: './lighthouse/caddy.setup.ts',
  use: {
    baseURL: CADDY_URL,
    // Caddy's certificate for localhost comes from its own local authority.
    ignoreHTTPSErrors: true,
    locale: 'es-AR',
    timezoneId: 'America/Argentina/Buenos_Aires',
    trace: 'retain-on-failure',
  },
  projects: [
    // The administrator's session, as in the end-to-end tests, to create the products and upload their photos.
    { name: 'setup', testDir: './tests', testMatch: /admin\.setup\.ts/ },
    {
      name: 'lighthouse',
      use: { ...devices['Pixel 7'] },
      dependencies: ['setup'],
    },
  ],
  webServer: [
    {
      command: `${mvnw} -q spring-boot:test-run -Dspring-boot.run.main-class=com.pijamaparty.store.E2eStoreApplication`,
      cwd: '../backend',
      url: `${BACKEND_URL}/api/health`,
      reuseExistingServer: !isCi,
      timeout: 600_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
});
