import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { problem, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = es.admin.sizeGuidePage;
const GUIDE = '/api/admin/catalog/categories/{id}/size-guide';
const CATEGORY = { id: 'cat-1', name: 'Pijamas de mujer' };

const WOMEN = {
  columns: ['Busto (cm)', 'Cintura (cm)'],
  rows: [
    { size: 'S', values: ['83–87', '65–69'] },
    { size: 'M', values: ['88–93', '70–75'] },
  ],
  note: 'Medí sobre la ropa interior.',
  updatedAt: '2026-10-18T12:00:00Z',
};

/** Opens the page of a category with {@code guide}; a save answers {@code put}, by default the guide as sent. */
async function openPage(
  guide: unknown = null,
  put: unknown = (options: { body?: unknown }) => answer(200, { ...(options.body as object), updatedAt: 'now' }),
) {
  const api = stubApi({
    'GET /api/admin/auth/me': signedIn,
    [`GET ${GUIDE}`]: answer(200, { category: CATEGORY, guide }),
    [`PUT ${GUIDE}`]: put,
    [`DELETE ${GUIDE}`]: answer(204),
  });
  renderRoute('/admin/categorias/cat-1/guia-de-talles');
  await screen.findByRole('heading', { level: 1, name: messages.heading(CATEGORY.name) });
  return api;
}

const columns = () => within(screen.getByRole('group', { name: messages.columnsTitle }));
const row = (position: number) => within(screen.getByRole('group', { name: messages.rowTitle(position) }));

describe('size guide of a category (RNF-31.5)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a category without a guide, when the administrator fills one and saves, then the whole guide is sent', async () => {
    const api = await openPage();
    expect(screen.getByText(messages.noGuide)).toBeInTheDocument();

    await userEvent.type(columns().getByLabelText(messages.columnLabel(1)), 'Edad');
    await userEvent.type(row(1).getByLabelText(messages.sizeLabel), '4');
    await userEvent.type(row(1).getByLabelText('Edad'), '4 años');
    await userEvent.click(screen.getByRole('button', { name: messages.addRow }));
    await userEvent.type(row(2).getByLabelText(messages.sizeLabel), '6');
    await userEvent.type(row(2).getByLabelText('Edad'), '6 años');
    await userEvent.click(screen.getByRole('button', { name: es.admin.save }));

    expect(await screen.findByRole('status')).toHaveTextContent(es.admin.saved);
    expect(api.callsTo(`PUT ${GUIDE}`)[0]?.options).toMatchObject({
      params: { path: { id: 'cat-1' } },
      body: {
        columns: ['Edad'],
        rows: [
          { size: '4', values: ['4 años'] },
          { size: '6', values: ['6 años'] },
        ],
      },
    });
    expect(api.callsTo(`PUT ${GUIDE}`)[0]?.options.body).not.toHaveProperty('note');
    expect(screen.getByRole('button', { name: messages.delete })).toBeInTheDocument();
  });

  it('given a guide, when a column is removed and a row moved up, then the values follow their columns and rows', async () => {
    const api = await openPage(WOMEN);
    expect(row(2).getByLabelText('Cintura (cm)')).toHaveValue('70–75');
    expect(screen.getByLabelText(messages.note)).toHaveValue('Medí sobre la ropa interior.');

    await userEvent.click(screen.getByRole('button', { name: messages.removeColumn('Busto (cm)') }));
    await userEvent.click(screen.getByRole('button', { name: messages.moveUp(2) }));
    await userEvent.click(screen.getByRole('button', { name: messages.addColumn }));
    await userEvent.click(screen.getByRole('button', { name: es.admin.save }));

    await screen.findByRole('status');
    expect(api.callsTo(`PUT ${GUIDE}`)[0]?.options.body).toEqual({
      columns: ['Cintura (cm)', ''],
      rows: [
        { size: 'M', values: ['70–75', ''] },
        { size: 'S', values: ['65–69', ''] },
      ],
      note: 'Medí sobre la ropa interior.',
    });
  });

  it('given a guide with a row that lacks a value, when the server refuses it, then the rows are marked', async () => {
    await openPage(WOMEN, problem(422, { problem: 'INVALID_SIZE_GUIDE', fields: ['rows'] }));

    await userEvent.click(screen.getByRole('button', { name: es.admin.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.invalid);
    expect(screen.getByText(messages.invalidRows)).toBeInTheDocument();
    expect(row(1).getByLabelText(messages.sizeLabel)).toHaveAttribute('aria-invalid', 'true');
    expect(columns().getByLabelText(messages.columnLabel(1))).toHaveAttribute('aria-invalid', 'false');
  });

  it('given a guide, when the administrator deletes it after confirming, then the form starts empty', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const api = await openPage(WOMEN);

    await userEvent.click(screen.getByRole('button', { name: messages.delete }));

    expect(await screen.findByRole('status')).toHaveTextContent(messages.deleted);
    expect(api.callsTo(`DELETE ${GUIDE}`)).toHaveLength(1);
    expect(columns().getByLabelText(messages.columnLabel(1))).toHaveValue('');
    expect(screen.queryByRole('button', { name: messages.delete })).not.toBeInTheDocument();
  });

  it('given a category that no longer exists, when its guide opens, then it says so', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [`GET ${GUIDE}`]: problem(404),
    });

    renderRoute('/admin/categorias/gone/guia-de-talles');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.notFound);
  });
});
