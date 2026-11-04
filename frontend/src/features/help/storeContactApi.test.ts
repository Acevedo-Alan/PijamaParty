import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { answer } from '../../test/catalogApi.ts';

// setup.ts replaces this module for every other test; this one checks the real request.
const { fetchStoreContact } = await vi.importActual<typeof import('./storeContactApi.ts')>('./storeContactApi.ts');

describe('store contact request (UC-36)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the stored contact data, when it is read, then the values come as they are and the null ones are left out', async () => {
    const get = vi.spyOn(api, 'GET').mockResolvedValue(
      answer(200, {
        email: null,
        whatsAppNumber: '5493424000000',
        openingHours: 'Lunes a viernes de 9 a 18 h',
        address: null,
        instagramUrl: 'https://www.instagram.com/pijamaparty.zzz/',
        facebookUrl: 'https://www.facebook.com/pijamaparty',
        tiktokUrl: null,
        announcement: 'Envío gratis a todo Santa Fe',
      }),
    );

    await expect(fetchStoreContact()).resolves.toEqual({
      whatsAppNumber: '5493424000000',
      openingHours: 'Lunes a viernes de 9 a 18 h',
      instagramUrl: 'https://www.instagram.com/pijamaparty.zzz/',
      facebookUrl: 'https://www.facebook.com/pijamaparty',
      announcement: 'Envío gratis a todo Santa Fe',
    });
    expect(get).toHaveBeenCalledWith('/api/store/contact', expect.anything());
  });

  it('given the request fails, when it is read, then it rejects, so the store shows none of it', async () => {
    vi.spyOn(api, 'GET').mockResolvedValue(answer(500));

    await expect(fetchStoreContact()).rejects.toThrow('500');
  });
});
