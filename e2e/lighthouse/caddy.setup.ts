import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { request } from 'node:https';
import { join } from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';
import { CADDY_HTTPS_PORT, CADDY_URL } from '../stubs/settings.ts';

/**
 * T-E6-02: the production Caddy image (deploy/caddy/Dockerfile: the real Caddyfile and the frontend build) in front
 * of the e2e backend on the host, so Lighthouse measures the compression, HTTP/2 and cache headers shoppers get. A
 * global setup rather than a webServer, so the container is removed at the end on every platform.
 */
const IMAGE = 'pijamaparty-caddy:lighthouse';
const CONTAINER = 'pijamaparty-lighthouse-caddy';
const REPOSITORY = join(import.meta.dirname, '..', '..');
/** The backend's e2e uploads directory (application-e2e.yml), served by Caddy as /uploads like the uploads volume. */
const UPLOADS = join(REPOSITORY, 'backend', 'target', 'e2e-uploads');
const STARTUP_TIMEOUT_MS = 60_000;

function docker(args: string[]): void {
  execFileSync('docker', args, { cwd: REPOSITORY, stdio: 'inherit' });
}

function removeContainer(): void {
  spawnSync('docker', ['rm', '--force', CONTAINER], { stdio: 'ignore' });
}

/** Whether Caddy answers the storefront; its certificate comes from Caddy's own local authority. */
function serving(): Promise<boolean> {
  return new Promise((resolve) => {
    const probe = request(CADDY_URL, { rejectUnauthorized: false, timeout: 2_000 }, (response) => {
      response.resume();
      resolve(response.statusCode === 200);
    });
    probe.on('error', () => {
      resolve(false);
    });
    probe.on('timeout', () => {
      probe.destroy();
    });
    probe.end();
  });
}

export default async function startCaddy(): Promise<() => void> {
  // Created here, as the current user: otherwise Docker would create the bind mount's directory as root.
  mkdirSync(UPLOADS, { recursive: true });
  // A run stopped halfway may have left the container behind.
  removeContainer();
  docker(['build', '--tag', IMAGE, '--file', 'deploy/caddy/Dockerfile', '.']);
  docker([
    'run', '--detach', '--rm', '--name', CONTAINER,
    '--env', 'DOMAIN=localhost',
    // Loopback only: the test store with its known admin password never reaches the local network
    '--publish', `127.0.0.1:${String(CADDY_HTTPS_PORT)}:443`,
    // The Caddyfile proxies /api to "api:8080": here, the backend on the host
    '--add-host', 'api:host-gateway',
    '--volume', `${UPLOADS}:/srv/uploads:ro`,
    IMAGE,
  ]);
  const deadline = Date.now() + STARTUP_TIMEOUT_MS;
  while (!(await serving())) {
    if (Date.now() > deadline) {
      removeContainer();
      throw new Error(`Caddy did not answer on ${CADDY_URL}`);
    }
    await sleep(500);
  }
  return removeContainer;
}
