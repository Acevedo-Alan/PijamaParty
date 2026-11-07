import { execFileSync, spawn } from 'node:child_process';
import { join } from 'node:path';

/**
 * T-E6-19 (RNF-28): runs sqlmap from its own image (security/sqlmap/Dockerfile) against one request and says what it
 * found. sqlmap exits with 0 whether or not it found an injection, so the verdict comes from its report.
 */
const IMAGE = 'pijamaparty-sqlmap:1.10.11';
const CONTEXT = join(import.meta.dirname, 'sqlmap');

export interface SqlmapTarget {
  /** What the report calls it, e.g. "GET /api/catalog/suggestions". */
  name: string;
  /** The URL as the proxy serves it, with `*` where a path segment is tested. */
  path: string;
  method?: 'GET' | 'POST';
  /** A JSON body: sqlmap tests each of its fields. */
  json?: unknown;
}

export type Verdict =
  | { kind: 'clean'; output: string }
  | { kind: 'injectable'; output: string }
  | { kind: 'inconclusive'; output: string };

/** Builds the image once; Docker's cache makes the next calls instant. */
export function buildSqlmapImage(): void {
  execFileSync('docker', ['build', '--quiet', '--tag', IMAGE, CONTEXT], { stdio: ['ignore', 'ignore', 'inherit'] });
}

/**
 * Level 3 and risk 2 add the tests on every parameter of the request (the JSON fields, the query values, the OR-based
 * boolean tests) to the default ones, with all the techniques (boolean, error, union, stacked and time-based), against
 * PostgreSQL only: the database is known, and guessing the others would only add minutes. Every 4xx of the API is an
 * ordinary answer, not a reason to stop.
 */
export function runSqlmap(proxyPort: number, target: SqlmapTarget): Promise<Verdict> {
  const method = target.method ?? 'GET';
  const args = [
    'run', '--rm', '--add-host', 'host.docker.internal:host-gateway', IMAGE,
    '--url', `http://host.docker.internal:${String(proxyPort)}${target.path}`,
    '--batch', '--disable-coloring', '--flush-session', '--skip-waf',
    '--dbms', 'PostgreSQL', '--level', '3', '--risk', '2', '--time-sec', '3', '--threads', '1',
    '--ignore-code', '400,401,403,404,409,422,423,429',
    '--output-dir', '/tmp/sqlmap-output',
    ...(method === 'POST' ? ['--method', 'POST'] : []),
    ...(target.json === undefined
      ? []
      : ['--data', JSON.stringify(target.json), '--headers', 'Content-Type: application/json']),
  ];
  return new Promise((resolve, reject) => {
    const sqlmap = spawn('docker', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    const collect = (chunk: Buffer) => {
      output += chunk.toString('utf8');
    };
    sqlmap.stdout.on('data', collect);
    sqlmap.stderr.on('data', collect);
    sqlmap.on('error', reject);
    sqlmap.on('close', () => {
      resolve({ kind: verdictOf(output), output });
    });
  });
}

/** Exposed for the report's wording, which changes between sqlmap releases. */
export function verdictOf(output: string): Verdict['kind'] {
  if (output.includes('sqlmap identified the following injection point')) {
    return 'injectable';
  }
  if (output.includes('do not appear to be injectable') || output.includes('does not seem to be injectable')) {
    return 'clean';
  }
  return 'inconclusive';
}
