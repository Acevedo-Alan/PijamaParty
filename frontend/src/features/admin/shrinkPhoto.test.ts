import { afterEach, describe, expect, it, vi } from 'vitest';
import { fitWithin, JPEG_QUALITY, shrinkPhoto, uploadName } from './shrinkPhoto.ts';

function photo(name: string, type: string): File {
  return new File(['pixels'], name, { type });
}

/** A browser that decodes a photo of the given size and draws it on a canvas that encodes to {@code encoded}. */
function stubBrowser(size: { width: number; height: number }, encoded: Blob | null = new Blob(['small'], { type: 'image/jpeg' })) {
  const close = vi.fn();
  const decode = vi.fn(() => Promise.resolve({ ...size, close }));
  vi.stubGlobal('createImageBitmap', decode);
  const drawImage = vi.fn();
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage } as never);
  const toBlob = vi
    .spyOn(HTMLCanvasElement.prototype, 'toBlob')
    .mockImplementation(function toBlob(this: HTMLCanvasElement, callback: BlobCallback) {
      callback(encoded);
    });
  return { decode, drawImage, toBlob, close };
}

describe('shrinking photos on the device (UC-06, T-E1-22)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('given a photo larger than 1600 px, when fitted, then its longest side becomes 1600 keeping the proportions', () => {
    expect(fitWithin({ width: 4000, height: 3000 })).toEqual({ width: 1600, height: 1200 });
    expect(fitWithin({ width: 3000, height: 4000 })).toEqual({ width: 1200, height: 1600 });
    expect(fitWithin({ width: 1600, height: 900 })).toBeNull();
  });

  it('given an 8 MB phone photo, when shrunk, then it is drawn at 1600 px with its orientation and sent as JPEG 0.85', async () => {
    const browser = stubBrowser({ width: 4032, height: 3024 });
    const file = photo('IMG_0001.jpg', 'image/jpeg');

    const shrunk = await shrinkPhoto(file);

    expect(shrunk).not.toBe(file);
    expect(shrunk.type).toBe('image/jpeg');
    expect(browser.decode).toHaveBeenCalledWith(file, { imageOrientation: 'from-image' });
    expect(browser.drawImage).toHaveBeenCalledWith(expect.anything(), 0, 0, 1600, 1200);
    expect(browser.toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', JPEG_QUALITY);
    expect(browser.close).toHaveBeenCalled();
  });

  it('given a large PNG, when shrunk, then it stays PNG', async () => {
    const browser = stubBrowser({ width: 2000, height: 2000 }, new Blob(['png'], { type: 'image/png' }));

    await shrinkPhoto(photo('logo.png', 'image/png'));

    expect(browser.toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/png', JPEG_QUALITY);
  });

  it('given a photo that already fits, when shrunk, then it is sent as it is', async () => {
    const browser = stubBrowser({ width: 1200, height: 900 });
    const file = photo('chica.jpg', 'image/jpeg');

    expect(await shrinkPhoto(file)).toBe(file);
    expect(browser.toBlob).not.toHaveBeenCalled();
  });

  it('given a photo the browser cannot decode or encode, when shrunk, then it is sent as it is for the server to judge', async () => {
    const file = photo('rara.jpg', 'image/jpeg');
    vi.stubGlobal('createImageBitmap', () => Promise.reject(new Error('cannot decode')));
    expect(await shrinkPhoto(file)).toBe(file);

    stubBrowser({ width: 3000, height: 3000 }, null);
    expect(await shrinkPhoto(file)).toBe(file);

    vi.stubGlobal('createImageBitmap', undefined);
    expect(await shrinkPhoto(file)).toBe(file);
  });

  it('given a WebP turned into JPEG, when named, then its extension says JPEG', () => {
    const webp = photo('foto.webp', 'image/webp');
    const jpeg = new Blob(['x'], { type: 'image/jpeg' });

    expect(uploadName(webp, jpeg)).toBe('foto.jpg');
    expect(uploadName(webp, webp)).toBe('foto.webp');
    expect(uploadName(photo('foto.JPEG', 'image/jpeg'), jpeg)).toBe('foto.JPEG');
  });
});
