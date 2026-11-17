/**
 * Dev only: the store with real MercadoPago sandbox payments (`npm run sandbox`). Opens a Cloudflare quick tunnel to
 * Vite, so MercadoPago can deliver signed webhooks, then starts Vite (allowing the tunnel's
 * host) and the dev backend (throwaway PostgreSQL, demo catalog) with PUBLIC_URL set to the tunnel. Ctrl+C stops all.
 *
 * `npm run sandbox:caddy` (T-E3-31): the production Caddy image (deploy/caddy/Dockerfile: the real Caddyfile, its
 * content security policy and the production build of the storefront) instead of Vite, as `npm run lighthouse` runs
 * it, so a purchase checks that the CSP lets MercadoPago's real card form load. Needs Docker; the first build takes a
 * few minutes. The tunnel goes to Caddy's HTTPS with the Host it serves (localhost), and the photos are the dev
 * backend's UPLOADS_DIR, mounted as production mounts its uploads volume.
 *
 * Reads the repository root's `.env.sandbox` (git-ignored):
 *   MERCADOPAGO_ACCESS_TOKEN    the access token of the application's "Credenciales de prueba" (APP_USR-...)
 *   MERCADOPAGO_PUBLIC_KEY      the public key of the same credentials (APP_USR-...), for the card form (T-E3-29)
 *   MERCADOPAGO_WEBHOOK_SECRET  the secret key of the same application's Webhooks ("Modo de prueba", event "Order
 *                               (Mercado Pago)", T-E3-34)
 *   VIPS_BIN_DIR                optional, libvips' bin folder for the photos
 *   CLOUDFLARED                 optional, the cloudflared executable
 *
 * The quick tunnel's URL changes on every run: paste the printed webhook URL in the MercadoPago panel each time.
 */
import { type ChildProcess, execFileSync, spawn, spawnSync } from 'node:child_process';
import { createWriteStream, existsSync, mkdirSync } from 'node:fs';
import { connect } from 'node:net';
import { delimiter, join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '../..');
const FRONTEND = join(ROOT, 'frontend');
const BACKEND = join(ROOT, 'backend');
const LOGS = join(BACKEND, 'target', 'sandbox');
const WINDOWS = process.platform === 'win32';
const DEFAULT_CLOUDFLARED = 'C:\\Program Files (x86)\\cloudflared\\cloudflared.exe';

/** T-E3-31: the production Caddy image in front instead of Vite. */
const CADDY = process.argv.includes('--caddy');
const CADDY_IMAGE = 'pijamaparty-caddy:sandbox';
const CADDY_CONTAINER = 'pijamaparty-sandbox-caddy';
/** Apart from the Lighthouse run's 8443, so both can be up at once. */
const CADDY_PORT = 8444;
/** Where the dev backend writes the photos (application.yml's default), resolved as Vite resolves it. */
const UPLOADS = resolve(process.env.UPLOADS_DIR ?? '/srv/uploads');

const children: ChildProcess[] = [];
let stopping = false;

function fail(message: string): never {
  console.error(`\n${message}`);
  stopAll();
  process.exit(1);
}

function required(name: string): string {
  const value = process.env[name];
  if (value === undefined || value === '') {
    fail(`${name} is missing in .env.sandbox`);
  }
  return value;
}

function portInUse(port: number): Promise<boolean> {
  return new Promise((done) => {
    const socket = connect(port, 'localhost');
    socket.once('connect', () => {
      socket.destroy();
      done(true);
    });
    socket.once('error', () => {
      done(false);
    });
  });
}

/** Starts a process whose output goes to its log file; its early exit stops everything. */
function start(name: string, command: string, args: string[], cwd: string, env: NodeJS.ProcessEnv): ChildProcess {
  const log = createWriteStream(join(LOGS, `${name}.log`));
  const child = spawn(command, args, { cwd, env, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(log);
  child.stderr.pipe(log);
  child.once('exit', (code) => {
    if (!stopping) {
      fail(`${name} stopped (exit code ${String(code)}), see ${join(LOGS, `${name}.log`)}`);
    }
  });
  children.push(child);
  return child;
}

function stopAll(): void {
  stopping = true;
  for (const child of children) {
    if (child.pid === undefined || child.exitCode !== null) {
      continue;
    }
    if (WINDOWS) {
      // mvnw and Vite start children of their own: kill the whole tree
      try {
        execFileSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' });
      } catch {
        // already gone
      }
    } else {
      child.kill('SIGTERM');
    }
  }
  if (CADDY) {
    removeCaddy();
  }
}

function removeCaddy(): void {
  spawnSync('docker', ['rm', '--force', CADDY_CONTAINER], { stdio: 'ignore' });
}

/** Builds and starts the production Caddy image on 127.0.0.1:{CADDY_PORT}, proxying /api to the backend on the host. */
function startCaddy(): void {
  mkdirSync(UPLOADS, { recursive: true });
  // A run stopped halfway may have left the container behind.
  removeCaddy();
  console.log('Building the production Caddy image (a few minutes the first time)...');
  const build = spawnSync('docker', ['build', '--tag', CADDY_IMAGE, '--file', 'deploy/caddy/Dockerfile', '.'], {
    cwd: ROOT,
    stdio: ['ignore', 'ignore', 'inherit'],
  });
  if (build.status !== 0) {
    fail('docker build of deploy/caddy/Dockerfile failed: is Docker running?');
  }
  const run = spawnSync('docker', [
    'run', '--detach', '--rm', '--name', CADDY_CONTAINER,
    '--env', 'DOMAIN=localhost',
    // Loopback only: only the tunnel reaches it
    '--publish', `127.0.0.1:${String(CADDY_PORT)}:443`,
    // The Caddyfile proxies /api to "api:8080": here, the dev backend on the host
    '--add-host', 'api:host-gateway',
    '--volume', `${UPLOADS}:/srv/uploads:ro`,
    CADDY_IMAGE,
  ], { cwd: ROOT, stdio: ['ignore', 'ignore', 'inherit'] });
  if (run.status !== 0) {
    fail('docker run of the Caddy image failed');
  }
}

/** T-E3-31: the page must come with the production CSP, or the purchase would not test it. */
async function checkCsp(url: string): Promise<void> {
  const csp = (await fetch(url)).headers.get('content-security-policy') ?? '';
  if (!csp.includes('https://sdk.mercadopago.com')) {
    fail(`The store at ${url} answered without the production CSP: is the tunnel going to Caddy?`);
  }
}

function tunnelUrl(tunnel: ChildProcess): Promise<string> {
  return new Promise((done, reject) => {
    const timer = setTimeout(() => {
      reject(new Error('cloudflared printed no URL within 60 s'));
    }, 60_000);
    tunnel.stderr?.on('data', (chunk: Buffer) => {
      const match = /https:\/\/[a-z0-9-]+\.trycloudflare\.com/.exec(chunk.toString());
      if (match !== null) {
        clearTimeout(timer);
        done(match[0]);
      }
    });
  });
}

async function waitForHealth(url: string): Promise<void> {
  const deadline = Date.now() + 240_000;
  while (Date.now() < deadline) {
    try {
      if ((await fetch(`${url}/api/health`)).ok) {
        return;
      }
    } catch {
      // not up yet
    }
    await new Promise((wait) => setTimeout(wait, 2_000));
  }
  fail(`The backend did not answer ${url}/api/health within 4 minutes, see ${join(LOGS, 'backend.log')}`);
}

const envFile = join(ROOT, '.env.sandbox');
if (!existsSync(envFile)) {
  fail(`Create ${envFile} with MERCADOPAGO_ACCESS_TOKEN, MERCADOPAGO_PUBLIC_KEY and MERCADOPAGO_WEBHOOK_SECRET (see this script's header)`);
}
process.loadEnvFile(envFile);
const accessToken = required('MERCADOPAGO_ACCESS_TOKEN');
const publicKey = required('MERCADOPAGO_PUBLIC_KEY');
const webhookSecret = required('MERCADOPAGO_WEBHOOK_SECRET');
const vipsBinDir = process.env.VIPS_BIN_DIR ?? '';
const cloudflared = process.env.CLOUDFLARED ?? (existsSync(DEFAULT_CLOUDFLARED) ? DEFAULT_CLOUDFLARED : 'cloudflared');

for (const port of [8080, CADDY ? CADDY_PORT : 5173]) {
  if (await portInUse(port)) {
    fail(`Port ${String(port)} is in use: stop the backend, Vite or Caddy that is already running`);
  }
}

process.once('SIGINT', () => {
  console.log(`\nStopping the backend, ${CADDY ? 'Caddy' : 'Vite'} and the tunnel...`);
  stopAll();
  process.exit(0);
});

mkdirSync(LOGS, { recursive: true });

if (CADDY) {
  startCaddy();
}

console.log('Opening the tunnel...');
// To Caddy: its HTTPS with its own local certificate, asked for the site it serves (DOMAIN=localhost).
const tunnelArgs = CADDY
  ? ['tunnel', '--no-autoupdate', '--url', `https://localhost:${String(CADDY_PORT)}`, '--no-tls-verify',
      '--http-host-header', 'localhost', '--origin-server-name', 'localhost']
  : ['tunnel', '--no-autoupdate', '--url', 'http://localhost:5173'];
const tunnel = start('tunnel', cloudflared, tunnelArgs, ROOT, process.env);
const publicUrl = await tunnelUrl(tunnel).catch((error: unknown) => fail(String(error)));

console.log(`Starting ${CADDY ? '' : 'Vite and '}the backend (about 40 s)...`);
if (!CADDY) {
  start('vite', process.execPath, [join(FRONTEND, 'node_modules', 'vite', 'bin', 'vite.js')], FRONTEND, {
    ...process.env,
    __VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS: new URL(publicUrl).hostname,
  });
}
start(
  'backend',
  WINDOWS ? 'cmd.exe' : './mvnw',
  WINDOWS ? ['/d', '/s', '/c', 'mvnw.cmd spring-boot:test-run@dev'] : ['spring-boot:test-run@dev'],
  BACKEND,
  {
    ...process.env,
    MERCADOPAGO_ACCESS_TOKEN: accessToken,
    MERCADOPAGO_PUBLIC_KEY: publicKey,
    MERCADOPAGO_WEBHOOK_SECRET: webhookSecret,
    PUBLIC_URL: publicUrl,
    ...(vipsBinDir === '' ? {} : { VIPS_BIN_DIR: vipsBinDir, PATH: `${vipsBinDir}${delimiter}${process.env.PATH ?? ''}` }),
  },
);

await waitForHealth(publicUrl);
if (CADDY) {
  await checkCsp(publicUrl);
}

console.log(`
Ready. Open the store at ${publicUrl} (not localhost).
${CADDY ? '\nServed by the production Caddy image with its CSP: during the purchase, the browser console must show no\n"Content-Security-Policy" errors (turn the ad blocker off for this site first).\n' : ''}
Webhook URL for the MercadoPago panel (Webhooks, Modo de prueba, event "Order (Mercado Pago)"):
  ${publicUrl}/api/payments/mercadopago/webhook

The demo catalog's shipping costs arrive last: until then the checkout says there is no shipping.
Logs: ${LOGS}
Ctrl+C stops everything.`);
