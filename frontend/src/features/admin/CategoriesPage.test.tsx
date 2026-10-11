import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { category, problem, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = es.admin.categoryList;
const pijamas = category('c-1', 'Pijamas');
const batas = category('c-2', 'Batas', false);
const listed = answer(200, [pijamas, batas]);

async function findRow(name: string) {
  await screen.findByText(name);
  return rowOf(name);
}

function rowOf(name: string) {
  const row = screen.getByText(name).closest('li');
  if (row === null) {
    throw new Error(`No row for ${name}`);
  }
  return within(row);
}

describe('category management (UC-05)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given categories, when the page opens, then every one shows by position with its state', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, 'GET /api/admin/catalog/categories': listed });

    renderRoute('/admin/categorias');

    const items = await screen.findAllByRole('listitem');
    expect(items.map((item) => item.querySelector('.admin-card-title')?.textContent)).toEqual(['Pijamas', 'Batas']);
    expect(rowOf('Batas').getByText(messages.inactive)).toBeInTheDocument();
    expect(rowOf('Pijamas').queryByText(messages.inactive)).not.toBeInTheDocument();
  });

  it('given an existing category name, when another with the same slug is created, then it is rejected with a clear message', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': listed,
      'POST /api/admin/catalog/categories': problem(409, { slug: 'pijamas' }),
    });
    renderRoute('/admin/categorias');

    await userEvent.type(await screen.findByLabelText(messages.newName), 'Pijamas');
    await userEvent.click(screen.getByRole('button', { name: messages.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.slugUsed('pijamas'));
    expect(api.callsTo('POST /api/admin/catalog/categories')[0]?.options.body).toEqual({ name: 'Pijamas' });
    expect(screen.getByLabelText(messages.newName)).toHaveValue('Pijamas');
  });

  it('given a new name, when created, then the field empties and the list reloads', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': [listed, answer(200, [pijamas, batas, category('c-3', 'Medias')])],
      'POST /api/admin/catalog/categories': answer(201, category('c-3', 'Medias')),
    });
    renderRoute('/admin/categorias');

    await userEvent.type(await screen.findByLabelText(messages.newName), 'Medias');
    await userEvent.click(screen.getByRole('button', { name: messages.create }));

    expect(await screen.findByText('Medias')).toBeInTheDocument();
    expect(screen.getByLabelText(messages.newName)).toHaveValue('');
    expect(api.callsTo('GET /api/admin/catalog/categories')).toHaveLength(2);
  });

  it('given a category with products, when deletion is attempted, then only deactivation is offered', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': listed,
      'DELETE /api/admin/catalog/categories/{id}': problem(409),
    });
    renderRoute('/admin/categorias');

    await userEvent.click((await findRow('Pijamas')).getByRole('button', { name: messages.delete }));

    expect(confirm).toHaveBeenCalledWith(messages.confirmDelete('Pijamas'));
    expect(await screen.findByRole('alert')).toHaveTextContent(messages.hasProducts);
    expect(rowOf('Pijamas').getByRole('button', { name: messages.deactivate })).toBeEnabled();
  });

  it('given a confirmation refused, when deleting, then nothing is sent', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const api = stubApi({ 'GET /api/admin/auth/me': signedIn, 'GET /api/admin/catalog/categories': listed });
    renderRoute('/admin/categorias');

    await userEvent.click((await findRow('Pijamas')).getByRole('button', { name: messages.delete }));

    expect(api.callsTo('DELETE /api/admin/catalog/categories/{id}')).toHaveLength(0);
  });

  it('given the first category, when moved down, then the whole new order is sent', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': listed,
      'PUT /api/admin/catalog/categories/order': answer(200, [batas, pijamas]),
    });
    renderRoute('/admin/categorias');

    expect(await screen.findByRole('button', { name: messages.moveUp('Pijamas') })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: messages.moveDown('Pijamas') }));

    expect(api.callsTo('PUT /api/admin/catalog/categories/order')[0]?.options.body).toEqual({
      categoryIds: ['c-2', 'c-1'],
    });
  });

  it('given an inactive category, when activated, then it is sent to the server', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': listed,
      'POST /api/admin/catalog/categories/{id}/activate': answer(200, category('c-2', 'Batas')),
    });
    renderRoute('/admin/categorias');

    await userEvent.click((await findRow('Batas')).getByRole('button', { name: messages.activate }));

    expect(await screen.findByRole('status')).toHaveTextContent(es.admin.saved);
    expect(api.callsTo('POST /api/admin/catalog/categories/{id}/activate')[0]?.options.params?.path).toEqual({
      id: 'c-2',
    });
  });

  it('given a category, when renamed in place, then the new name is sent and the form closes', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/catalog/categories': listed,
      'PATCH /api/admin/catalog/categories/{id}': answer(200, category('c-1', 'Pijamas de invierno')),
    });
    renderRoute('/admin/categorias');

    await userEvent.click((await findRow('Pijamas')).getByRole('button', { name: messages.rename }));
    const field = screen.getByLabelText(messages.renameLabel('Pijamas'));
    await userEvent.clear(field);
    await userEvent.type(field, 'Pijamas de invierno');
    await userEvent.click(screen.getByRole('button', { name: es.admin.save }));

    expect(await screen.findByRole('status')).toHaveTextContent(es.admin.saved);
    expect(screen.queryByLabelText(messages.renameLabel('Pijamas'))).not.toBeInTheDocument();
    expect(api.callsTo('PATCH /api/admin/catalog/categories/{id}')[0]?.options.body).toEqual({
      name: 'Pijamas de invierno',
    });
  });
});
