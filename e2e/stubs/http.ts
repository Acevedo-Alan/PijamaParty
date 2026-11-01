import type { IncomingMessage, ServerResponse } from 'node:http';

/** Small helpers the stubs share: no framework, only node:http. */

export async function readBody(request: IncomingMessage): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of request) {
    chunks.push(chunk as Buffer);
  }
  return Buffer.concat(chunks).toString('utf8');
}

export function sendJson(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify(body));
}

export function hasBearer(request: IncomingMessage, token: string): boolean {
  return request.headers.authorization === `Bearer ${token}`;
}
