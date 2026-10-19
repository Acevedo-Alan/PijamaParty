import { spawn } from 'node:child_process';
import { join } from 'node:path';
import { createInterface } from 'node:readline';
import { CADDY_CONTAINER } from '../lighthouse/caddy.setup.ts';

/**
 * T-E6-14: runs one k6 script of load/k6 with the k6 image, so nothing is installed; k6 runs TypeScript itself. It
 * joins the network of the Caddy container: Caddy is `localhost` and the backend is `api:8080`, as Caddy sees them.
 */
const K6_IMAGE = 'grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34';
const SCRIPTS = join(import.meta.dirname, 'k6');
const SHOWN_WARNINGS = 5;

/**
 * Resolves when k6 passes every threshold and check; rejects otherwise. The summary goes to the console. k6 warns
 * once per failed request, thousands of times under saturation (refused connections, which the script counts), so
 * those warnings are summed up in one line after the first few.
 */
export function runK6(script: string, env: Record<string, string> = {}): Promise<void> {
  const variables = Object.entries(env).flatMap(([name, value]) => ['--env', `${name}=${value}`]);
  const args = [
    'run', '--rm', '--network', `container:${CADDY_CONTAINER}`,
    '--volume', `${SCRIPTS}:/scripts:ro`,
    K6_IMAGE, 'run', '--quiet', '--no-color', ...variables, `/scripts/${script}`,
  ];
  return new Promise((resolve, reject) => {
    const k6 = spawn('docker', args, { stdio: ['ignore', 'inherit', 'pipe'] });
    let failedRequests = 0;
    createInterface({ input: k6.stderr }).on('line', (line) => {
      if (line.includes('msg="Request Failed"')) {
        failedRequests++;
        // The first few still show, for the reason.
        if (failedRequests <= SHOWN_WARNINGS) {
          process.stderr.write(`${line}\n`);
        }
      } else {
        process.stderr.write(`${line}\n`);
      }
    });
    k6.on('error', reject);
    k6.on('close', (code) => {
      if (failedRequests > 0) {
        console.log(`k6 ${script}: ${String(failedRequests)} requests got no HTTP answer (first ${String(SHOWN_WARNINGS)} shown)`);
      }
      if (code === 0) {
        resolve();
      } else {
        // 99: a threshold was missed (see the summary above).
        reject(new Error(`k6 ${script} ended with exit code ${String(code)}`));
      }
    });
  });
}
