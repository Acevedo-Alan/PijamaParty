import { afterEach, describe, expect, it, vi } from 'vitest';
import { uploadTransport } from './uploadTransport.ts';

type Listener = (event: { lengthComputable: boolean; loaded: number; total: number }) => void;

/** The last request a fake XMLHttpRequest received; the test plays the network on it. */
let last: FakeXhr | undefined;

class FakeXhr {
  method = '';
  url = '';
  headers: Record<string, string> = {};
  body: unknown;
  status = 0;
  responseText = '';
  private readonly listeners = new Map<string, () => void>();
  private progress: Listener | undefined;
  readonly upload = {
    addEventListener: (_type: string, listener: Listener) => {
      this.progress = listener;
    },
  };

  constructor() {
    // eslint-disable-next-line @typescript-eslint/no-this-alias
    last = this;
  }

  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }

  setRequestHeader(name: string, value: string) {
    this.headers[name] = value;
  }

  addEventListener(type: string, listener: () => void) {
    this.listeners.set(type, listener);
  }

  send(body: unknown) {
    this.body = body;
  }

  sent(loaded: number, total: number) {
    this.progress?.({ lengthComputable: true, loaded, total });
  }

  answer(status: number, text: string) {
    this.status = status;
    this.responseText = text;
    this.listeners.get('load')?.();
  }

  drop() {
    this.listeners.get('error')?.();
  }
}

describe('photo upload transport (T-E1-22)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('given a photo, when sent, then it goes as multipart with its key, the CSRF token and its progress', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeXhr);
    document.cookie = 'XSRF-TOKEN=token-1';
    const progress: number[] = [];
    const file = new Blob(['jpg'], { type: 'image/jpeg' });

    const reply = uploadTransport.send({
      url: '/api/admin/catalog/products/p-1/images',
      file,
      fileName: 'luna.jpg',
      idempotencyKey: 'key-1',
      onProgress: (sent) => progress.push(sent),
    });
    last?.sent(50, 100);
    last?.answer(201, '{"id":"i-1"}');

    await expect(reply).resolves.toEqual({ status: 201, body: { id: 'i-1' } });
    expect(last?.method).toBe('POST');
    expect(last?.headers).toMatchObject({ 'Idempotency-Key': 'key-1', 'X-XSRF-TOKEN': 'token-1' });
    const part = (last?.body as FormData).get('file') as File;
    expect(part.name).toBe('luna.jpg');
    expect(progress).toEqual([0.5]);
  });

  it('given a lost connection, when sending, then it rejects as fetch does', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeXhr);

    const reply = uploadTransport.send({ url: '/x', file: new Blob(['x']), fileName: 'x.jpg', idempotencyKey: 'k' });
    last?.drop();

    await expect(reply).rejects.toThrow(TypeError);
  });
});
