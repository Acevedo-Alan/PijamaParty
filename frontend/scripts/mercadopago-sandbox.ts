/**
 * Dev only: the store with real MercadoPago sandbox payments (`npm run sandbox`). Opens a Cloudflare quick tunnel to
 * Vite, so MercadoPago can send the buyer back and deliver signed webhooks, then starts Vite (allowing the tunnel's
 * host) and the dev backend (throwaway PostgreSQL, demo catalog) with PUBLIC_URL set to the tunnel. Ctrl+C stops all.
 *
 * Reads the repository root's `.env.sandbox` (git-ignored):
 *   MERCADOPAGO_ACCESS_TOKEN    the test seller's production access token (APP_USR-...)
 *   MERCADOPAGO_WEBHOOK_SECRET  the secret key of the application's Webhooks ("Modo productivo", event Pagos)
 *   VIPS_BIN_DIR                optional, libvips' bin folder for the photos
 *   CLOUDFLARED                 optional, the cloudflared executable
 *
 * The quick tunnel's URL changes on every run: paste the printed webhook URL in the MercadoPago panel each time.
 */
import { type ChildProcess, execFileSync, spawn } from 'node:child_process';
import { createWriteStream, existsSync, mkdirSync } from 'node:fs';
import { connect } from 'node:net';
import { delimiter, join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '../..');
const FRONTEND = join(ROOT, 'frontend');
const BACKEND = join(ROOT, 'backend');
const LOGS = join(BACKEND, 'target', 'sandbox');
const WINDOWS = process.platform === 'win32';
const DEFAULT_CLOUDFLARED = 'C:\\Program Files (x86)\\cloudflared\\cloudflared.exe';

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
  fail(`Create ${envFile} with MERCADOPAGO_ACCESS_TOKEN and MERCADOPAGO_WEBHOOK_SECRET (see this script's header)`);
}
process.loadEnvFile(envFile);
const accessToken = required('MERCADOPAGO_ACCESS_TOKEN');
const webhookSecret = required('MERCADOPAGO_WEBHOOK_SECRET');
const vipsBinDir = process.env.VIPS_BIN_DIR ?? '';
const cloudflared = process.env.CLOUDFLARED ?? (existsSync(DEFAULT_CLOUDFLARED) ? DEFAULT_CLOUDFLARED : 'cloudflared');

for (const port of [8080, 5173]) {
  if (await portInUse(port)) {
    fail(`Port ${String(port)} is in use: stop the backend or Vite that is already running`);
  }
}

process.once('SIGINT', () => {
  console.log('\nStopping the backend, Vite and the tunnel...');
  stopAll();
  process.exit(0);
});

mkdirSync(LOGS, { recursive: true });

console.log('Opening the tunnel...');
const tunnel = start('tunnel', cloudflared, ['tunnel', '--no-autoupdate', '--url', 'http://localhost:5173'], ROOT, process.env);
const publicUrl = await tunnelUrl(tunnel).catch((error: unknown) => fail(String(error)));

console.log('Starting Vite and the backend (about 40 s)...');
start('vite', process.execPath, [join(FRONTEND, 'node_modules', 'vite', 'bin', 'vite.js')], FRONTEND, {
  ...process.env,
  __VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS: new URL(publicUrl).hostname,
});
start(
  'backend',
  WINDOWS ? 'cmd.exe' : './mvnw',
  WINDOWS ? ['/d', '/s', '/c', 'mvnw.cmd spring-boot:test-run@dev'] : ['spring-boot:test-run@dev'],
  BACKEND,
  {
    ...process.env,
    MERCADOPAGO_ACCESS_TOKEN: accessToken,
    MERCADOPAGO_WEBHOOK_SECRET: webhookSecret,
    PUBLIC_URL: publicUrl,
    ...(vipsBinDir === '' ? {} : { VIPS_BIN_DIR: vipsBinDir, PATH: `${vipsBinDir}${delimiter}${process.env.PATH ?? ''}` }),
  },
);

await waitForHealth(publicUrl);

console.log(`
Ready. Open the store at ${publicUrl} (not localhost).

Webhook URL for the MercadoPago panel (Webhooks, Modo productivo, event Pagos):
  ${publicUrl}/api/payments/mercadopago/webhook

Logs: ${LOGS}
Ctrl+C stops everything.`);
