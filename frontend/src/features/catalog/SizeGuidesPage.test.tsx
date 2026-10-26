import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { answer, listed, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = es.sizeGuides;

const GUIDES = answer(200, {
  items: [
    {
      category: { slug: 'pijamas-de-mujer', name: 'Pijamas de mujer' },
      columns: ['Busto (cm)', 'Cintura (cm)'],
      rows: [
        { size: 'M', values: ['88–93', '70–75'] },
        { size: 'S', values: ['83–87', ''] },
      ],
      note: 'Medí sobre la ropa interior.',
    },
    {
      category: { slug: 'pijamas-de-ninos', name: 'Pijamas de niños' },
      columns: ['Edad'],
      rows: [{ size: '4', values: ['4 años'] }],
      note: null,
    },
  ],
});

describe('size guide page (UC-31, T-E7-29)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given guides of two active categories, when the page opens, then each shows its columns, its rows in order and its note', async () => {
    stubCatalogApi({ listings: [listed([])], sizeGuides: GUIDES });

    renderRoute('/guia-de-talles');

    const women = within(await screen.findByRole('region', { name: 'Pijamas de mujer' }));
    const table = within(women.getByRole('region', { name: messages.tableOf('Pijamas de mujer') }));
    expect(table.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      es.product.size,
      'Busto (cm)',
      'Cintura (cm)',
    ]);
    expect(table.getAllByRole('rowheader').map((cell) => cell.textContent)).toEqual(['M', 'S']);
    expect(table.getAllByRole('row')[2]).toHaveTextContent(`S83–87${es.product.noMeasure}`);
    expect(women.getByText('Medí sobre la ropa interior.')).toBeInTheDocument();
    const kids = within(screen.getByRole('region', { name: 'Pijamas de niños' }));
    expect(kids.getByRole('cell', { name: '4 años' })).toBeInTheDocument();
    expect(within(screen.getByRole('main')).getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)).toEqual([
      'Pijamas de mujer',
      'Pijamas de niños',
    ]);
    expect(document.querySelector('meta[name="description"]')).toHaveAttribute('content', es.seo.sizeGuidesDescription);
  });

  it('given no guides, when the page opens, then it says there are none yet and links the FAQ', async () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/guia-de-talles');

    expect(await screen.findByText(messages.none, { exact: false })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.faq })).toHaveAttribute('href', '/preguntas-frecuentes');
  });

  it('given the guides failing, when the page opens, then it shows the error and Reintentar loads them again', async () => {
    const get = stubCatalogApi({ listings: [listed([])], sizeGuides: answer(500) });

    renderRoute('/guia-de-talles');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.loadError);
    const calls = () => get.mock.calls.filter(([path]) => path === '/api/catalog/size-guides').length;
    const before = calls();
    await userEvent.click(screen.getByRole('button', { name: messages.retry }));
    expect(calls()).toBeGreaterThan(before);
  });
});
