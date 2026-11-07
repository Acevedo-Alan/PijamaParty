import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { fetchProductDetail } from './productDetailApi.ts';

/** T-E6-02: the product that index.html asked for before the app loaded is used once, for that product only. */
describe('fetchProductDetail with the early request', () => {
  afterEach(() => {
    window.earlyProduct = undefined;
    vi.restoreAllMocks();
  });

  function stubApi() {
    return vi
      .spyOn(api, 'GET')
      .mockResolvedValue({ data: undefined, response: new Response(null, { status: 404 }) });
  }

  it('answers from the early request without asking again', async () => {
    window.earlyProduct = { slug: 'luna', response: Promise.resolve(new Response(null, { status: 404 })) };
    const get = stubApi();

    await expect(fetchProductDetail('luna')).resolves.toEqual({ status: 'notFound' });
    expect(get).not.toHaveBeenCalled();
    expect(window.earlyProduct).toBeUndefined();
  });

  it('asks itself for another product and keeps the early request for its own', async () => {
    const early = { slug: 'luna', response: Promise.resolve(new Response(null, { status: 404 })) };
    window.earlyProduct = early;
    const get = stubApi();

    await fetchProductDetail('sol');
    expect(get).toHaveBeenCalledOnce();
    expect(window.earlyProduct).toBe(early);
  });

  it('asks itself when the early request failed', async () => {
    window.earlyProduct = { slug: 'luna', response: Promise.reject(new TypeError('Failed to fetch')) };
    const get = stubApi();

    await expect(fetchProductDetail('luna')).resolves.toEqual({ status: 'notFound' });
    expect(get).toHaveBeenCalledOnce();
  });

  it('asks itself when the early answer is a server error', async () => {
    window.earlyProduct = { slug: 'luna', response: Promise.resolve(new Response(null, { status: 503 })) };
    const get = stubApi();

    await fetchProductDetail('luna');
    expect(get).toHaveBeenCalledOnce();
  });

  it('uses the early request only once', async () => {
    window.earlyProduct = { slug: 'luna', response: Promise.resolve(new Response(null, { status: 404 })) };
    const get = stubApi();

    await fetchProductDetail('luna');
    await fetchProductDetail('luna');
    expect(get).toHaveBeenCalledOnce();
  });
});
