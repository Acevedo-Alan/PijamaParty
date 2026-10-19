import { randomUUID } from 'node:crypto';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { hasBearer, readBody, sendJson } from './http.ts';
import { RESEND_API_KEY } from './settings.ts';

/**
 * T-E6-01: a stub of Resend's POST /emails (UC-17, ResendEmailSender), so the tests can count the emails an order
 * sends. They stay in memory only: never written to disk or to the log. GET /__e2e/emails?to=... lists those sent to
 * one address.
 */

interface SentEmail {
  id: string;
  to: string;
  subject: string;
}

interface EmailBody {
  to?: string | string[];
  subject?: string;
}

const emails: SentEmail[] = [];

export function startResendStub(port: number): Server {
  const server = createServer((request, response) => {
    handle(request, response).catch((error: unknown) => {
      console.error('Resend stub failed:', error);
      if (!response.headersSent) {
        sendJson(response, 500, { message: 'stub error' });
      }
    });
  });
  server.listen(port, '127.0.0.1');
  return server;
}

async function handle(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const url = new URL(request.url ?? '/', 'http://stub');
  const method = request.method ?? 'GET';

  if (method === 'GET' && url.pathname === '/__e2e/health') {
    sendJson(response, 200, { status: 'UP' });
  } else if (method === 'POST' && url.pathname === '/emails') {
    if (!hasBearer(request, RESEND_API_KEY)) {
      sendJson(response, 401, { message: 'invalid API key' });
      return;
    }
    const body = JSON.parse(await readBody(request)) as EmailBody;
    const to = Array.isArray(body.to) ? body.to[0] : body.to;
    if (to === undefined || body.subject === undefined) {
      sendJson(response, 422, { message: 'missing to or subject' });
      return;
    }
    const email: SentEmail = { id: randomUUID(), to, subject: body.subject };
    emails.push(email);
    sendJson(response, 200, { id: email.id });
  } else if (method === 'GET' && url.pathname === '/__e2e/emails') {
    const to = url.searchParams.get('to');
    sendJson(response, 200, emails.filter((email) => email.to === to));
  } else {
    sendJson(response, 404, { message: 'not found' });
  }
}
