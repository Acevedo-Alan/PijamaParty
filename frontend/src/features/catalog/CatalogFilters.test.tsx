import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { filterOptions, listed, product, queriesTo, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const PRODUCTS = '/api/catalog/products';

async function openFilters(button: string = es.filters.size) {
  await userEvent.click(await screen.findByRole('button', { name: new RegExp(`^${button}`) }));
  return screen.getByRole('dialog', { name: es.filters.title });
}

function group(panel: HTMLElement, legend: string) {
  return within(panel).getByRole('group', { name: legend });
}

// Opening the panel and ticking filters takes under 2 s alone, but over 5 s in a busy parallel run.
describe('catalog filters and sort (UC-02)', { timeout: 30_000 }, () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the filters size M and season summer, when applied, then the grid and the URL show them', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa'), product('celeste')]), listed([product('rosa')])] });
    const { router } = renderRoute('/');
    await screen.findByRole('link', { name: /Pijama celeste/ });

    const panel = await openFilters();
    await userEvent.click(within(group(panel, es.filters.size)).getByRole('checkbox', { name: 'M' }));
    await userEvent.click(within(group(panel, es.filters.season)).getByRole('checkbox', { name: 'Verano' }));
    await userEvent.click(within(panel).getByRole('button', { name: es.filters.apply }));

    await waitFor(() => {
      expect(screen.queryByRole('link', { name: /Pijama celeste/ })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: /Pijama rosa/ })).toBeInTheDocument();
    expect(queriesTo(get, PRODUCTS)).toEqual([{ page: 0 }, { size: ['M'], season: ['SUMMER'], page: 0 }]);
    expect(router.state.location.search).toBe('?talle=M&temporada=verano');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: es.filters.groupWithCount(es.filters.size, 1) })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: es.filters.groupWithCount(es.filters.season, 1) })).toBeInTheDocument();
  });

  it('given an applied filter, when the URL is shared, then whoever opens it sees the same filters', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/pijamas?talle=M&color=Rosa&temporada=verano&precio-hasta=20000&orden=menor-precio');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(queriesTo(get, PRODUCTS)).toEqual([
      { category: 'pijamas', size: ['M'], color: ['Rosa'], season: ['SUMMER'], maxPrice: 2_000_000, sort: 'PRICE_ASC', page: 0 },
    ]);
    expect(screen.getByRole('combobox', { name: es.sort.label })).toHaveValue('PRICE_ASC');
    const panel = await openFilters();
    expect(within(group(panel, es.filters.size)).getByRole('checkbox', { name: 'M' })).toBeChecked();
    expect(within(group(panel, es.filters.size)).getByRole('checkbox', { name: 'S' })).not.toBeChecked();
    expect(within(group(panel, es.filters.color)).getByRole('checkbox', { name: 'Rosa' })).toBeChecked();
    expect(within(group(panel, es.filters.season)).getByRole('checkbox', { name: 'Verano' })).toBeChecked();
    expect(within(panel).getByRole('spinbutton', { name: es.filters.maxPrice })).toHaveValue(20000);
  });

  it('given filters with no matches, when applied, then a message and a "clear filters" option are shown', async () => {
    const get = stubCatalogApi({ listings: [listed([]), listed([product('rosa')])] });
    const { router } = renderRoute('/?talle=XL&orden=mayor-precio');

    expect(await screen.findByText(es.catalog.noMatches)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: es.catalog.clearFilters }));

    expect(await screen.findByRole('link', { name: /Pijama rosa/ })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?orden=mayor-precio');
    expect(queriesTo(get, PRODUCTS)).toEqual([
      { size: ['XL'], sort: 'PRICE_DESC', page: 0 },
      { sort: 'PRICE_DESC', page: 0 },
    ]);
  });

  it('given a category without products and no filters, when it opens, then the empty-category message is kept', async () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/categoria/vacia?orden=menor-precio');

    expect(await screen.findByText(es.catalog.emptyCategory)).toBeInTheDocument();
    expect(screen.queryByText(es.catalog.noMatches)).not.toBeInTheDocument();
  });

  it('given the listing, when the sort changes, then it applies at once from the first page', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])] });
    const { router } = renderRoute('/?talle=M');
    await screen.findByRole('link', { name: /Pijama rosa/ });

    await userEvent.selectOptions(screen.getByRole('combobox', { name: es.sort.label }), es.sort.PRICE_DESC);

    await waitFor(() => {
      expect(queriesTo(get, PRODUCTS)).toEqual([
        { size: ['M'], page: 0 },
        { size: ['M'], sort: 'PRICE_DESC', page: 0 },
      ]);
    });
    expect(router.state.location.search).toBe('?talle=M&orden=mayor-precio');
  });

  it('given a price range, when applied, then the API receives cents and the URL keeps whole pesos', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])] });
    const { router } = renderRoute('/');

    const panel = await openFilters();
    expect(within(panel).getByText('Entre $ 12.000 y $ 34.500,50')).toBeInTheDocument();
    await userEvent.type(within(panel).getByRole('spinbutton', { name: es.filters.minPrice }), '15000');
    await userEvent.type(within(panel).getByRole('spinbutton', { name: es.filters.maxPrice }), '30000');
    await userEvent.click(within(panel).getByRole('button', { name: es.filters.apply }));

    await waitFor(() => {
      expect(queriesTo(get, PRODUCTS)).toContainEqual({ minPrice: 1_500_000, maxPrice: 3_000_000, page: 0 });
    });
    expect(router.state.location.search).toBe('?precio-desde=15000&precio-hasta=30000');
    expect(screen.getByRole('button', { name: es.filters.groupWithCount(es.filters.price, 1) })).toBeInTheDocument();
  });

  it('given a minimum above the maximum, when applied, then the panel refuses it and nothing is requested', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])] });
    const { router } = renderRoute('/');
    await screen.findByRole('link', { name: /Pijama rosa/ });

    const panel = await openFilters();
    await userEvent.type(within(panel).getByRole('spinbutton', { name: es.filters.minPrice }), '30000');
    await userEvent.type(within(panel).getByRole('spinbutton', { name: es.filters.maxPrice }), '10000');
    await userEvent.click(within(panel).getByRole('button', { name: es.filters.apply }));

    expect(within(panel).getByRole('alert')).toHaveTextContent(es.filters.priceRangeError);
    expect(within(panel).getByRole('spinbutton', { name: es.filters.minPrice })).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(queriesTo(get, PRODUCTS)).toHaveLength(1);
    expect(router.state.location.search).toBe('');
  });

  it('given choices in the panel, when it is closed without applying, then they are discarded', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])] });
    renderRoute('/');

    let panel = await openFilters();
    await userEvent.click(within(group(panel, es.filters.size)).getByRole('checkbox', { name: 'L' }));
    await userEvent.click(within(panel).getByRole('button', { name: es.filters.close }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    panel = await openFilters();
    expect(within(group(panel, es.filters.size)).getByRole('checkbox', { name: 'L' })).not.toBeChecked();
    expect(queriesTo(get, PRODUCTS)).toHaveLength(1);
  });

  it('given applied filters, when "Clear" and then "Show results" are pressed, then every filter goes', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])] });
    const { router } = renderRoute('/?talle=M&publico=mujer&precio-desde=1000&orden=menor-precio');

    const panel = await openFilters();
    await userEvent.click(within(panel).getByRole('button', { name: es.filters.clear }));

    expect(within(panel).getAllByRole('checkbox').filter((box) => (box as HTMLInputElement).checked)).toHaveLength(0);
    expect(within(panel).getByRole('spinbutton', { name: es.filters.minPrice })).toHaveValue(null);
    await userEvent.click(within(panel).getByRole('button', { name: es.filters.apply }));

    await waitFor(() => {
      expect(router.state.location.search).toBe('?orden=menor-precio');
    });
    expect(queriesTo(get, PRODUCTS).at(-1)).toEqual({ sort: 'PRICE_ASC', page: 0 });
  });

  it('given the filter options, when the panel opens, then it offers them in order and leaves out empty groups', async () => {
    stubCatalogApi({
      listings: [listed([product('rosa')])],
      filters: filterOptions({ materials: [], minPriceCents: null, maxPriceCents: null }),
    });
    renderRoute('/');

    const panel = await openFilters();

    const labels = (legend: string) =>
      within(group(panel, legend))
        .getAllByRole('checkbox')
        .map((box) => box.closest('label')?.textContent);
    expect(labels(es.filters.size)).toEqual(['S', 'M', 'L']);
    expect(labels(es.filters.color)).toEqual(['Rosa', 'Celeste']);
    expect(labels(es.filters.season)).toEqual(['Verano', 'Invierno']);
    expect(labels(es.filters.audience)).toEqual(['Mujer', 'Infantil']);
    expect(within(panel).queryByRole('group', { name: es.filters.material })).not.toBeInTheDocument();
    expect(within(panel).queryByRole('group', { name: es.filters.price })).not.toBeInTheDocument();
  });

  it('given a shared value no longer offered, when the panel opens, then it is still shown checked to remove it', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/?talle=XXL');

    const panel = await openFilters();

    expect(within(group(panel, es.filters.size)).getByRole('checkbox', { name: 'XXL' })).toBeChecked();
  });
});

describe('filter bar (T-E7-06, changes to UC-02)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  const bar = () => within(screen.getByRole('group', { name: es.filters.bar }));

  it('given the "Talle" button, when pressed, then the filter panel opens with the focus on the size group', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    renderRoute('/');

    const panel = await openFilters(es.filters.size);

    await waitFor(() => {
      expect(within(group(panel, es.filters.size)).getByRole('checkbox', { name: 'S' })).toHaveFocus();
    });
  });

  it('given the "Precio" button, when pressed, then the focus goes to the minimum price', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    renderRoute('/');

    const panel = await openFilters(es.filters.price);

    await waitFor(() => {
      expect(within(panel).getByRole('spinbutton', { name: es.filters.minPrice })).toHaveFocus();
    });
  });

  it('given the filter options, when the bar shows, then it has a button per group in order, without empty groups', async () => {
    stubCatalogApi({
      listings: [listed([product('rosa')])],
      filters: filterOptions({ materials: [], minPriceCents: null, maxPriceCents: null }),
    });
    renderRoute('/');

    await waitFor(() => {
      expect(bar().getAllByRole('button').map((button) => button.textContent)).toEqual([
        es.filters.audience,
        es.filters.size,
        es.filters.color,
        es.filters.season,
      ]);
    });
  });

  it('given values chosen in two groups, when the bar shows, then each of those buttons says how many', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    renderRoute('/?talle=S&talle=M&publico=mujer');

    await waitFor(() => {
      expect(bar().getByRole('button', { name: 'Talle (2)' })).toBeInTheDocument();
    });
    expect(bar().getByRole('button', { name: 'Público (1)' })).toBeInTheDocument();
    expect(bar().getByRole('button', { name: es.filters.color })).toBeInTheDocument();
  });

  it('given a shared value in a group without options, when the bar shows, then its button stays to remove it', async () => {
    stubCatalogApi({ listings: [listed([])], filters: filterOptions({ materials: [] }) });
    renderRoute('/?material=Polar');

    expect(await screen.findByRole('button', { name: 'Material (1)' })).toBeInTheDocument();
  });
});
