import { join } from 'node:path';
import { defineConfig } from '@playwright/test';
import { BACKEND_URL, CADDY_URL } from './stubs/settings.ts';

/**
 * T-E7-11: the store next to the approved prototype (docs/design/prototype/index.html) on a phone and a computer, as
 * PNG pairs in screenshots/ with an index.html that shows them side by side. Not a test that passes or fails: a person
 * compares the pairs. One command: `npm run screenshots`.
 *
 * Runs against the production Caddy image (lighthouse/caddy.setup.ts) so the store serves its product photos, which
 * needs libvips (vips on the PATH, or VIPS_BIN_DIR). The prototype loads its fonts and photos from the internet.
 */
// The Maven wrapper by its full path: Windows does not look for commands in the working directory.
const mvnw = `"${join(import.meta.dirname, '..', 'backend', process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw')}"`;

export default defineConfig({
  testDir: './screenshots',
  fullyParallel: false,
  workers: 1,
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
  },
  projects: [
    // The administrator's session, as in the end-to-end tests, to create the products and upload their photos.
    { name: 'setup', testDir: './tests', testMatch: /admin\.setup\.ts/ },
    { name: 'screenshots', dependencies: ['setup'] },
  ],
  webServer: [
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
