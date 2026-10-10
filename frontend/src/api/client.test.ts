import { afterEach, describe, expect, it } from 'vitest';
import { csrfMiddleware, readCsrfToken } from './client.ts';

type OnRequest = NonNullable<typeof csrfMiddleware.onRequest>;

async function send(method: string, url = `${window.location.origin}/api/cart`): Promise<Request> {
  const request = new Request(url, { method });
  const onRequest = csrfMiddleware.onRequest as OnRequest;
  const result = await onRequest({ request } as Parameters<OnRequest>[0]);
  return result instanceof Request ? result : request;
}

describe('api client', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('given the CSRF cookie among others, when reading it, then its decoded value is returned', () => {
    expect(readCsrfToken('a=1; XSRF-TOKEN=abc%3D%3D; b=2')).toBe('abc==');
    expect(readCsrfToken('a=1')).toBeUndefined();
  });

  it('given a state-changing request, when sent, then it carries the CSRF token in X-XSRF-TOKEN', async () => {
    document.cookie = 'XSRF-TOKEN=token-123';

    expect((await send('POST')).headers.get('X-XSRF-TOKEN')).toBe('token-123');
  });

  it('given a request to another origin, when sent, then the CSRF token never leaves the store', async () => {
    document.cookie = 'XSRF-TOKEN=token-123';

    expect((await send('POST', 'https://third-party.example/collect')).headers.get('X-XSRF-TOKEN')).toBeNull();
  });

  it('given a safe request, when sent, then it does not carry the CSRF header', async () => {
    document.cookie = 'XSRF-TOKEN=token-123';

    expect((await send('GET')).headers.get('X-XSRF-TOKEN')).toBeNull();
  });
});
