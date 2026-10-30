import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { esAdmin } from '../../i18n/esAdmin.ts';
import {
  adminProduct,
  category,
  connectionLost,
  photo,
  problem,
  signedIn,
  stubApi,
  variant,
} from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { uploadTransport, type UploadReply } from './uploadTransport.ts';

const edit = esAdmin.productEdit;
const variants = esAdmin.variants;
const photos = esAdmin.photos;
const categories = answer(200, [category('c-1', 'Pijamas')]);

const sizes = ['S', 'M', 'L'];
const colors = ['Rosa', 'Celeste'];
const matrix = colors.flatMap((color) => sizes.map((size) => variant(size, color)));

function section(name: string) {
  return within(screen.getByRole('region', { name }));
}

describe('product edit (UC-06)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given 3 sizes and 2 colors, when the matrix is generated, then 6 variants are created and listed by color', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, adminProduct()),
      'POST /api/admin/catalog/products/{id}/variants/generate': answer(
        200,
        adminProduct({ sizes, colors, variants: matrix.toReversed() }),
      ),
    });
    renderRoute('/admin/productos/p-1');

    await userEvent.type(await screen.findByLabelText(variants.sizes), 'S, M ,L');
    await userEvent.type(screen.getByLabelText(variants.colors), 'Rosa,Celeste');
    await userEvent.click(screen.getByRole('button', { name: variants.generate }));

    expect(await screen.findByText(variants.created(6))).toBeInTheDocument();
    expect(api.callsTo('POST /api/admin/catalog/products/{id}/variants/generate')[0]?.options.body).toEqual({
      sizes,
      colors,
    });
    const names = section(variants.title)
      .getAllByRole('listitem')
      .map((item) => item.querySelector('.admin-card-title')?.textContent);
    expect(names).toEqual(['S · Rosa', 'M · Rosa', 'L · Rosa', 'S · Celeste', 'M · Celeste', 'L · Celeste']);
    expect(screen.getByLabelText(variants.sizes)).toHaveValue('');
  });

  it('given repeated sizes, when generating, then the problem is explained', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, adminProduct()),
      'POST /api/admin/catalog/products/{id}/variants/generate': problem(422, { problem: 'REPEATED_SIZES' }),
    });
    renderRoute('/admin/productos/p-1');

    await userEvent.type(await screen.findByLabelText(variants.sizes), 'S, S');
    await userEvent.type(screen.getByLabelText(variants.colors), 'Rosa');
    await userEvent.click(screen.getByRole('button', { name: variants.generate }));

    expect(await screen.findByRole('alert')).toHaveTextContent(variants.problems.REPEATED_SIZES);
  });

  it('given a variant, when its price is typed in pesos, then it is saved in cents', async () => {
    const product = adminProduct({ sizes: ['M'], colors: ['Rosa'], variants: [variant('M', 'Rosa')] });
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, product),
      'PATCH /api/admin/catalog/variants/{id}': answer(
        200,
        { ...product, variants: [variant('M', 'Rosa', 1_250_050)] },
      ),
    });
    renderRoute('/admin/productos/p-1');

    await userEvent.type(await screen.findByLabelText(variants.priceLabel('M', 'Rosa')), '12.500,50');
    await userEvent.click(screen.getByRole('button', { name: variants.savePrice }));

    expect(await screen.findByText(esAdmin.saved)).toBeInTheDocument();
    const call = api.callsTo('PATCH /api/admin/catalog/variants/{id}')[0];
    expect(call?.options.params?.path).toEqual({ id: 'v-M-Rosa' });
    expect(call?.options.body).toEqual({ priceCents: 1_250_050 });
  });

  it('given a price that is not an amount, when saved, then nothing is sent', async () => {
    const product = adminProduct({ sizes: ['M'], colors: ['Rosa'], variants: [variant('M', 'Rosa')] });
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, product),
    });
    renderRoute('/admin/productos/p-1');

    await userEvent.type(await screen.findByLabelText(variants.priceLabel('M', 'Rosa')), 'doce mil');
    await userEvent.click(screen.getByRole('button', { name: variants.savePrice }));

    expect(screen.getByRole('alert')).toHaveTextContent(variants.priceInvalid);
    expect(api.callsTo('PATCH /api/admin/catalog/variants/{id}')).toHaveLength(0);
  });

  it('given the last active priced variant of a published product, when deactivated, then it is refused', async () => {
    const product = adminProduct({
      published: true,
      sizes: ['M'],
      colors: ['Rosa'],
      variants: [variant('M', 'Rosa', 2_500_000)],
    });
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, product),
      'PATCH /api/admin/catalog/variants/{id}': problem(409),
    });
    renderRoute('/admin/productos/p-1');

    await userEvent.click(await screen.findByRole('checkbox', { name: variants.activeLabel('M', 'Rosa') }));

    expect(await screen.findByRole('alert')).toHaveTextContent(variants.lastActivePriced);
    expect(screen.getByRole('checkbox', { name: variants.activeLabel('M', 'Rosa') })).toBeChecked();
    expect(api.callsTo('PATCH /api/admin/catalog/variants/{id}')[0]?.options.body).toEqual({ active: false });
  });

  it('given a variant, when shown, then it links to its stock', async () => {
    const product = adminProduct({ sizes: ['M'], colors: ['Rosa'], variants: [variant('M', 'Rosa')] });
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, product),
    });

    renderRoute('/admin/productos/p-1');

    expect(await screen.findByRole('link', { name: variants.stockLink('M', 'Rosa') })).toHaveAttribute(
      'href',
      '/admin/productos/p-1/variantes/v-M-Rosa/stock',
    );
  });

  it('given a hidden product without priced variants, when published, then it asks for one', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, adminProduct()),
      'POST /api/admin/catalog/products/{id}/publish': problem(409),
    });
    renderRoute('/admin/productos/p-1');

    await userEvent.click(await screen.findByRole('button', { name: edit.publish }));

    expect(await screen.findByRole('alert')).toHaveTextContent(edit.needsPricedVariant);
  });

  it('given a hidden product, when published, then it shows as published with a link to the store', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, adminProduct()),
      'POST /api/admin/catalog/products/{id}/publish': answer(200, adminProduct({ published: true })),
    });
    renderRoute('/admin/productos/p-1');

    await userEvent.click(await screen.findByRole('button', { name: edit.publish }));

    expect(await screen.findByText(edit.published)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: edit.hide })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: edit.viewInStore })).toHaveAttribute('href', '/producto/pijama-luna');
  });

  it('given changed details, when saved, then they are sent', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, adminProduct()),
      'PUT /api/admin/catalog/products/{id}': answer(200, adminProduct({ material: 'Satén' })),
    });
    renderRoute('/admin/productos/p-1');

    const material = await screen.findByLabelText(esAdmin.productForm.material);
    await userEvent.clear(material);
    await userEvent.type(material, 'Satén');
    await userEvent.click(section(edit.details).getByRole('button', { name: esAdmin.save }));

    expect(await section(edit.details).findByRole('status')).toHaveTextContent(esAdmin.saved);
    expect(api.callsTo('PUT /api/admin/catalog/products/{id}')[0]?.options.body).toMatchObject({
      categoryId: 'c-1',
      name: 'Pijama Luna',
      material: 'Satén',
    });
  });

  it('given an unknown product, when opened, then it says so', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/products/{id}': problem(404),
    });

    renderRoute('/admin/productos/nope');

    expect(await screen.findByRole('heading', { level: 1, name: edit.notFound })).toBeInTheDocument();
  });
});

describe('product photos (UC-06)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  const withColors = adminProduct({ colors: ['Rosa'], images: [photo('i-1'), photo('i-2', 'Rosa')] });
  const jpg = () => new File(['jpg'], 'luna.jpg', { type: 'image/jpeg' });

  it('given photos, when shown, then each has its position and color, with the first unable to go up', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, withColors),
    });

    renderRoute('/admin/productos/p-1');

    expect(await screen.findByRole('img', { name: photos.alt(1, null) })).toHaveAttribute('src', '/uploads/i-1-400.webp');
    expect(screen.getByRole('img', { name: photos.alt(2, 'Rosa') })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: photos.moveUp(1) })).toBeDisabled();
    expect(screen.getByRole('button', { name: photos.moveDown(2) })).toBeDisabled();
  });

  const routes = {
    'GET /api/admin/auth/me': signedIn,
    'GET /api/admin/catalog/categories': categories,
    'GET /api/admin/catalog/products/{id}': answer(200, withColors),
  };

  /** Answers the uploads in order (the last one repeats), reporting half the body sent first. */
  function stubUploads(...replies: (UploadReply | Error)[]) {
    const queue = [...replies];
    return vi.spyOn(uploadTransport, 'send').mockImplementation((request) => {
      request.onProgress?.(0.5);
      const next = queue.length > 1 ? queue.shift() : queue[0];
      return next instanceof Error ? Promise.reject(next) : Promise.resolve(next as UploadReply);
    });
  }

  const created: UploadReply = { status: 201, body: photo('i-3', 'Rosa') };

  it('given an upload cut by a lost connection, when retried, then the same key and bytes are sent', async () => {
    const api = stubApi(routes);
    const send = stubUploads(connectionLost(), created);
    renderRoute('/admin/productos/p-1');

    await userEvent.selectOptions(await screen.findByLabelText(photos.color), 'Rosa');
    await userEvent.upload(screen.getByLabelText(photos.file), jpg());
    expect(await screen.findByRole('alert')).toHaveTextContent(photos.connectionLost);
    await userEvent.click(screen.getByRole('button', { name: photos.retryOne('luna.jpg') }));

    expect(await screen.findByText(photos.states.uploaded)).toBeInTheDocument();
    const [first, retry] = send.mock.calls.map(([request]) => request);
    expect(first?.idempotencyKey).toMatch(/^[0-9a-f-]{36}$/);
    expect(retry?.idempotencyKey).toBe(first?.idempotencyKey);
    expect(retry?.file).toBe(first?.file);
    expect(retry?.url).toBe('/api/admin/catalog/products/p-1/images?color=Rosa');
    expect(retry?.fileName).toBe('luna.jpg');
    expect(api.callsTo('GET /api/admin/catalog/products/{id}')).toHaveLength(2);
  });

  it('given several photos picked at once, when uploaded, then they go one after the other, each with its key', async () => {
    stubApi(routes);
    const send = stubUploads(created);
    renderRoute('/admin/productos/p-1');

    await userEvent.upload(await screen.findByLabelText(photos.file), [
      jpg(),
      new File(['png'], 'sol.png', { type: 'image/png' }),
    ]);

    await vi.waitFor(() => {
      expect(screen.getAllByText(photos.states.uploaded)).toHaveLength(2);
    });
    const requests = send.mock.calls.map(([request]) => request);
    expect(requests.map((request) => request.fileName)).toEqual(['luna.jpg', 'sol.png']);
    expect(requests.map((request) => request.url)).toEqual([
      '/api/admin/catalog/products/p-1/images',
      '/api/admin/catalog/products/p-1/images',
    ]);
    expect(new Set(requests.map((request) => request.idempotencyKey)).size).toBe(2);
    expect(screen.getByRole('progressbar', { name: photos.progress('sol.png') })).toHaveAttribute('value', '1');
  });

  it('given an upload in flight, when part of it is sent, then its progress shows', async () => {
    stubApi(routes);
    vi.spyOn(uploadTransport, 'send').mockImplementation((request) => {
      request.onProgress?.(0.4);
      return new Promise(() => undefined);
    });
    renderRoute('/admin/productos/p-1');

    await userEvent.upload(await screen.findByLabelText(photos.file), jpg());

    const bar = await screen.findByRole('progressbar', { name: photos.progress('luna.jpg') });
    await vi.waitFor(() => {
      expect(bar).toHaveAttribute('value', '0.4');
    });
    expect(screen.getByText(photos.states.uploading)).toBeInTheDocument();
  });

  it('given the server busy converting, when uploading, then the photo can be retried', async () => {
    stubApi(routes);
    stubUploads({ status: 503, body: undefined }, created);
    renderRoute('/admin/productos/p-1');

    await userEvent.upload(await screen.findByLabelText(photos.file), jpg());
    expect(await screen.findByRole('alert')).toHaveTextContent(photos.busy);
    await userEvent.click(screen.getByRole('button', { name: photos.retryOne('luna.jpg') }));

    expect(await screen.findByText(photos.states.uploaded)).toBeInTheDocument();
  });

  it.each([
    ['a file that is not JPG, PNG or WebP', { status: 422, body: { problem: 'UNSUPPORTED_FORMAT' } }, photos.problems.UNSUPPORTED_FORMAT],
    ['a product with 8 photos', { status: 409, body: {} }, photos.tooMany],
    ['a file over 10 MB', { status: 413, body: {} }, photos.fileTooLarge],
  ])('given %s, when uploaded, then it is rejected with its reason', async (_case, reply, message) => {
    stubApi(routes);
    stubUploads(reply);
    renderRoute('/admin/productos/p-1');

    await userEvent.upload(await screen.findByLabelText(photos.file), jpg());

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByText(photos.states.rejected)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: photos.retryOne('luna.jpg') })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: photos.dismiss('luna.jpg') }));
    expect(screen.queryByText(photos.states.rejected)).not.toBeInTheDocument();
  });

  it('given two photos, when the first is dragged onto the second, then the whole new order is sent', async () => {
    const api = stubApi({
      ...routes,
      'PUT /api/admin/catalog/products/{id}/images/order': answer(
        200,
        { ...withColors, images: [photo('i-2', 'Rosa'), photo('i-1')] },
      ),
    });
    renderRoute('/admin/productos/p-1');
    const first = (await screen.findByRole('img', { name: photos.alt(1, null) })).closest('li');
    const second = screen.getByRole('img', { name: photos.alt(2, 'Rosa') }).closest('li');

    fireEvent.dragStart(first as HTMLElement);
    fireEvent.dragOver(second as HTMLElement);
    fireEvent.drop(second as HTMLElement);

    expect(await screen.findByRole('img', { name: photos.alt(1, 'Rosa') })).toBeInTheDocument();
    expect(api.callsTo('PUT /api/admin/catalog/products/{id}/images/order')[0]?.options.body).toEqual({
      imageIds: ['i-2', 'i-1'],
    });
  });

  it('given two photos, when the first moves down, then the whole new order is sent', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': answer(200, withColors),
      'PUT /api/admin/catalog/products/{id}/images/order': answer(
        200,
        { ...withColors, images: [photo('i-2', 'Rosa'), photo('i-1')] },
      ),
    });
    renderRoute('/admin/productos/p-1');

    await userEvent.click(await screen.findByRole('button', { name: photos.moveDown(1) }));

    expect(await screen.findByRole('img', { name: photos.alt(1, 'Rosa') })).toBeInTheDocument();
    expect(api.callsTo('PUT /api/admin/catalog/products/{id}/images/order')[0]?.options.body).toEqual({
      imageIds: ['i-2', 'i-1'],
    });
  });

  it('given a photo, when deleted after confirming, then it leaves the product', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products/{id}': [
        answer(200, withColors),
        answer(200, { ...withColors, images: [photo('i-2', 'Rosa')] }),
      ],
      'DELETE /api/admin/catalog/products/{id}/images/{imageId}': answer(204),
    });
    renderRoute('/admin/productos/p-1');

    await userEvent.click(await screen.findByRole('button', { name: photos.delete(1) }));

    expect(await screen.findByRole('img', { name: photos.alt(1, 'Rosa') })).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: photos.alt(2, 'Rosa') })).not.toBeInTheDocument();
    expect(api.callsTo('DELETE /api/admin/catalog/products/{id}/images/{imageId}')[0]?.options.params?.path).toEqual({
      id: 'p-1',
      imageId: 'i-1',
    });
  });
});
