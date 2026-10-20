import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { listed, product, queriesTo, shopCategories, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const PRODUCTS = '/api/catalog/products';

describe('search results page (UC-26)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a text in the URL, when the page opens, then its results come by relevance', async () => {
    const get = stubCatalogApi({ listings: [listed([product('luna')])] });

    renderRoute('/buscar?q=pijama+de+algod%C3%B3n');

    expect(await screen.findByRole('link', { name: /Pijama luna/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(es.search.resultsFor('pijama de algodón'));
    expect(queriesTo(get, PRODUCTS)).toEqual([{ q: 'pijama de algodón', page: 0 }]);
    expect(screen.getByRole('combobox', { name: es.sort.label })).toHaveValue('RELEVANCE');
    expect(screen.getByRole('combobox', { name: es.search.label })).toHaveValue('pijama de algodón');
  });

  it('given a search, when the sort and size M are chosen, then they apply together with the text', async () => {
    const get = stubCatalogApi({ listings: [listed([product('luna')])] });
    const { router } = renderRoute('/buscar?q=pijama');
    await screen.findByRole('link', { name: /Pijama luna/ });

    await userEvent.selectOptions(screen.getByRole('combobox', { name: es.sort.label }), 'NEWEST');
    await userEvent.click(screen.getByRole('button', { name: /^Talle/ }));
    const panel = screen.getByRole('dialog', { name: es.filters.title });
    const sizes = within(panel).getByRole('group', { name: es.filters.size });
    await userEvent.click(within(sizes).getByRole('checkbox', { name: 'M' }));
    await userEvent.click(within(panel).getByRole('button', { name: es.filters.apply }));

    await waitFor(() => {
      expect(router.state.location.search).toBe('?q=pijama&talle=M&orden=novedades');
    });
    expect(queriesTo(get, PRODUCTS).at(-1)).toEqual({ q: 'pijama', size: ['M'], sort: 'NEWEST', page: 0 });
  });

  it('given a search with no results, when shown, then a message and links to the categories appear', async () => {
    stubCatalogApi({
      listings: [listed([])],
      categories: shopCategories([
        { slug: 'verano', name: 'Verano' },
        { slug: 'invierno', name: 'Invierno' },
      ]),
    });

    renderRoute('/buscar?q=zzz');

    expect(await screen.findByText(es.search.noResults('zzz'))).toBeInTheDocument();
    const links = within(screen.getByRole('navigation', { name: es.search.categories }));
    expect(await links.findByRole('link', { name: 'Verano' })).toHaveAttribute('href', '/categoria/verano');
    expect(links.getByRole('link', { name: 'Invierno' })).toHaveAttribute('href', '/categoria/invierno');
    expect(links.getByRole('link', { name: es.catalog.seeAll })).toHaveAttribute('href', '/');
  });

  it('given filters with no matches, when shown, then they can be cleared keeping the text', async () => {
    stubCatalogApi({ listings: [listed([])] });
    const { router } = renderRoute('/buscar?q=pijama&talle=XL');

    await userEvent.click(await screen.findByRole('button', { name: es.catalog.clearFilters }));

    expect(router.state.location.search).toBe('?q=pijama');
  });

  it('given a text under 2 characters, when the page opens, then it asks for more and searches nothing', () => {
    const get = stubCatalogApi({ listings: [listed([])] });

    renderRoute('/buscar?q=+p+');

    expect(screen.getByText(es.search.tooShort)).toBeInTheDocument();
    expect(queriesTo(get, PRODUCTS)).toEqual([]);
  });

  it('given a text in the URL of the catalog, when it opens, then it lists without searching', async () => {
    const get = stubCatalogApi({ listings: [listed([product('luna')])] });

    renderRoute('/?q=pijama');

    await screen.findByRole('link', { name: /Pijama luna/ });
    expect(queriesTo(get, PRODUCTS)).toEqual([{ page: 0 }]);
  });
});
