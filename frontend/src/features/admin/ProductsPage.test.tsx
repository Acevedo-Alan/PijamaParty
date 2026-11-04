import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { adminProduct, category, problem, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = esAdmin.productList;
const form = esAdmin.productForm;
const categories = answer(200, [category('c-1', 'Pijamas'), category('c-2', 'Batas', false)]);

function summary(id: string, name: string, published: boolean, variantCount: number) {
  return { id, categoryId: 'c-1', name, slug: id, published, variantCount };
}

async function fillNewProduct() {
  await userEvent.type(await screen.findByLabelText(form.name), 'Pijama Luna');
  await userEvent.type(screen.getByLabelText(form.description), 'Pijama largo.');
  await userEvent.type(screen.getByLabelText(form.material), 'Algodón');
  await userEvent.selectOptions(screen.getByLabelText(form.season), es.filters.seasons.SUMMER);
  await userEvent.selectOptions(screen.getByLabelText(form.audience), es.filters.audiences.KIDS);
  await userEvent.selectOptions(screen.getByLabelText(form.category), form.inactiveCategory('Batas'));
}

describe('product list and creation (UC-06)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given products, when the list opens, then each shows category, state and variants, with more on request', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products': [
        answer(200, { items: [summary('p-2', 'Pijama Sol', true, 6)], hasMore: true }),
        answer(200, { items: [summary('p-1', 'Pijama Luna', false, 1)], hasMore: false }),
      ],
    });
    renderRoute('/admin/productos');

    const sun = within((await screen.findByRole('link', { name: 'Pijama Sol' })).closest('li') as HTMLElement);
    expect(sun.getByText(messages.published)).toBeInTheDocument();
    expect(await sun.findByText(`Pijamas · ${messages.variants(6)}`)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Pijama Sol' })).toHaveAttribute('href', '/admin/productos/p-2');

    await userEvent.click(screen.getByRole('button', { name: esAdmin.showMore }));

    expect(await screen.findByRole('link', { name: 'Pijama Luna' })).toBeInTheDocument();
    expect(screen.getByText(messages.hidden)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: esAdmin.showMore })).not.toBeInTheDocument();
    expect(api.callsTo('GET /api/admin/catalog/products').map((call) => call.options.params?.query)).toEqual([
      { page: 0 },
      { page: 1 },
    ]);
  });

  it('given filters in the address, when the list opens, then they are asked for and the fields show them (T-E8-08)', async () => {
    const robes = '6f1c2b3a-0000-4000-8000-0000000000c2';
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': answer(200, [category('c-1', 'Pijamas'), category(robes, 'Batas', false)]),
      'GET /api/admin/catalog/products': answer(200, { items: [], hasMore: false }),
    });
    renderRoute(`/admin/productos?buscar=coraz%C3%B3n&categoria=${robes}&estado=HIDDEN&stock=OUT`);

    expect(await screen.findByText(messages.emptyFiltered)).toBeInTheDocument();
    expect(api.callsTo('GET /api/admin/catalog/products')[0]?.options.params?.query).toEqual({
      page: 0,
      q: 'corazón',
      category: robes,
      state: 'HIDDEN',
      stock: 'OUT',
    });
    expect(screen.getByLabelText(messages.search)).toHaveValue('corazón');
    expect(screen.getByLabelText(messages.state)).toHaveValue('HIDDEN');
    expect(screen.getByLabelText(messages.stock)).toHaveValue('OUT');
    expect(await screen.findByRole('option', { name: form.inactiveCategory('Batas') })).toBeInTheDocument();
    expect(screen.getByLabelText(messages.category)).toHaveValue(robes);
  });

  it('given values the server would refuse in the address, when the list opens, then they are left out', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products': answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/productos?buscar=a&categoria=verano&estado=DRAFT&stock=SOME');

    expect(await screen.findByText(messages.empty)).toBeInTheDocument();
    expect(api.callsTo('GET /api/admin/catalog/products')[0]?.options.params?.query).toEqual({ page: 0 });
  });

  it('given a category in the address that no longer exists, when the list opens, then the select says so', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products': answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/productos?categoria=6f1c2b3a-0000-4000-8000-0000000000ff');

    expect(await screen.findByRole('option', { name: messages.unknownCategory })).toBeInTheDocument();
    expect(screen.getByLabelText(messages.category)).toHaveValue('6f1c2b3a-0000-4000-8000-0000000000ff');
  });

  it('given a search and a stock level, when applied, then both are asked for and stay in the address', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products': answer(200, { items: [summary('p-1', 'Pijama Luna', true, 2)], hasMore: false }),
    });
    const { router } = renderRoute('/admin/productos');
    const filters = within(await screen.findByRole('search', { name: messages.filters }));

    await userEvent.selectOptions(filters.getByLabelText(messages.stock), messages.stocks.LOW);
    await userEvent.type(filters.getByLabelText(messages.search), '  pp-ab12cd34 ');
    await userEvent.click(filters.getByRole('button', { name: messages.apply }));

    await vi.waitFor(() => {
      expect(api.callsTo('GET /api/admin/catalog/products').at(-1)?.options.params?.query).toEqual({
        page: 0,
        q: 'pp-ab12cd34',
        stock: 'LOW',
      });
    });
    const address = new URLSearchParams(router.state.location.search);
    expect(address.get('buscar')).toBe('pp-ab12cd34');
    expect(address.get('stock')).toBe('LOW');
    expect(filters.getByLabelText(messages.search)).toHaveAccessibleDescription(messages.searchHint);
  });

  it('given a search of one letter, when submitted, then the field says why and nothing is asked for', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'GET /api/admin/catalog/products': answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/productos');
    await screen.findByText(messages.empty);

    await userEvent.type(screen.getByLabelText(messages.search), 'a');
    await userEvent.click(screen.getByRole('button', { name: messages.apply }));

    const field = screen.getByLabelText(messages.search);
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field).toHaveFocus();
    expect(field).toHaveAccessibleDescription(`${messages.searchHint} ${messages.searchInvalid}`);
    expect(api.callsTo('GET /api/admin/catalog/products')).toHaveLength(1);
  });

  it('given the details of a new product, when created, then it is sent and opens to add variants', async () => {
    const created = adminProduct({ categoryId: 'c-2', season: 'SUMMER', audience: 'KIDS' });
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'POST /api/admin/catalog/products': answer(201, created),
    });
    const { router } = renderRoute('/admin/productos/nuevo');

    await fillNewProduct();
    await userEvent.click(screen.getByRole('button', { name: form.create }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/productos/p-1');
    expect(api.callsTo('POST /api/admin/catalog/products')[0]?.options.body).toEqual({
      categoryId: 'c-2',
      name: 'Pijama Luna',
      description: 'Pijama largo.',
      material: 'Algodón',
      season: 'SUMMER',
      audience: 'KIDS',
    });
  });

  it('given invalid fields, when created, then the fields the server refused are marked', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'POST /api/admin/catalog/products': problem(422, { fields: ['MATERIAL', 'NAME'] }),
    });
    renderRoute('/admin/productos/nuevo');

    await fillNewProduct();
    await userEvent.click(screen.getByRole('button', { name: form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(form.invalid);
    expect(screen.getByLabelText(form.name)).toHaveAccessibleDescription(form.errors.NAME);
    expect(screen.getByLabelText(form.material)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(form.description)).toHaveAttribute('aria-invalid', 'false');
  });

  it('given a name whose slug another product uses, when created, then the slug is shown', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': categories,
      'POST /api/admin/catalog/products': problem(409, { slug: 'pijama-luna' }),
    });
    renderRoute('/admin/productos/nuevo');

    await fillNewProduct();
    await userEvent.click(screen.getByRole('button', { name: form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(form.slugUsed('pijama-luna'));
  });

  it('given no categories, when creating a product, then it asks to create a category first', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, 'GET /api/admin/catalog/categories': answer(200, []) });

    renderRoute('/admin/productos/nuevo');

    expect(await screen.findByText(form.needsCategory)).toBeInTheDocument();
    expect(screen.queryByLabelText(form.name)).not.toBeInTheDocument();
  });
});
