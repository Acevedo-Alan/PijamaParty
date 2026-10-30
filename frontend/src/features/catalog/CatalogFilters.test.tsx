import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { filterOptions, listed, product, queriesTo, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const PRODUCTS = '/api/catalog/products';

async function openFilters(button: string = es.filters.size) {
  await userEvent.click(await screen.findByRole('button', { name: new RegExp(`^${button}`) }));
  return screen.getByRole('dialog', { name: es.filters.title });
}

/** A group of the panel, opened first when it starts collapsed. */
async function group(panel: HTMLElement, legend: string) {
  const fieldset = within(panel).getByRole('group', { name: legend });
  const toggle = within(fieldset).getByRole('button', { name: legend });
  if (toggle.getAttribute('aria-expanded') === 'false') {
    await userEvent.click(toggle);
  }
  return fieldset;
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
    await userEvent.click(within(await group(panel, es.filters.size)).getByRole('checkbox', { name: 'M' }));
    await userEvent.click(within(await group(panel, es.filters.season)).getByRole('checkbox', { name: 'Verano' }));
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
    expect(within(await group(panel, es.filters.size)).getByRole('checkbox', { name: 'M' })).toBeChecked();
    expect(within(await group(panel, es.filters.size)).getByRole('checkbox', { name: 'S' })).not.toBeChecked();
    expect(within(await group(panel, es.filters.color)).getByRole('checkbox', { name: 'Rosa' })).toBeChecked();
    expect(within(await group(panel, es.filters.season)).getByRole('checkbox', { name: 'Verano' })).toBeChecked();
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

    const panel = await openFilters(es.filters.price);
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

    const panel = await openFilters(es.filters.price);
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
    await userEvent.click(within(await group(panel, es.filters.size)).getByRole('checkbox', { name: 'L' }));
    await userEvent.click(within(panel).getByRole('button', { name: es.filters.close }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    panel = await openFilters();
    expect(within(await group(panel, es.filters.size)).getByRole('checkbox', { name: 'L' })).not.toBeChecked();
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

    const labels = async (legend: string) =>
      within(await group(panel, legend))
        .getAllByRole('checkbox')
        .map((box) => box.closest('label')?.textContent);
    expect(await labels(es.filters.size)).toEqual(['S', 'M', 'L']);
    expect(await labels(es.filters.color)).toEqual(['Rosa', 'Celeste']);
    expect(await labels(es.filters.season)).toEqual(['Verano', 'Invierno']);
    expect(await labels(es.filters.audience)).toEqual(['Mujer', 'Infantil']);
    expect(within(panel).queryByRole('group', { name: es.filters.material })).not.toBeInTheDocument();
    expect(within(panel).queryByRole('group', { name: es.filters.price })).not.toBeInTheDocument();
    const legends = within(panel)
      .getAllByRole('group')
      .map((fieldset) => fieldset.querySelector('legend button span')?.textContent);
    expect(legends).toEqual([es.filters.audience, es.filters.size, es.filters.color, es.filters.season]);
  });

  it('given a shared value no longer offered, when the panel opens, then it is still shown checked to remove it', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/?talle=XXL');

    const panel = await openFilters();

    expect(within(await group(panel, es.filters.size)).getByRole('checkbox', { name: 'XXL' })).toBeChecked();
  });
});

describe('organized filters panel (changes to UC-02)', { timeout: 30_000 }, () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  const toggleOf = (panel: HTMLElement, legend: string) =>
    within(within(panel).getByRole('group', { name: legend })).getByRole('button', { name: legend });

  it('given a button of the bar, when pressed, then its group and the groups with values open, the rest collapse', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    renderRoute('/?temporada=verano');

    const panel = await openFilters(es.filters.color);

    expect(toggleOf(panel, es.filters.color)).toHaveAttribute('aria-expanded', 'true');
    expect(toggleOf(panel, es.filters.season)).toHaveAttribute('aria-expanded', 'true');
    expect(toggleOf(panel, es.filters.size)).toHaveAttribute('aria-expanded', 'false');
    expect(toggleOf(panel, es.filters.material)).toHaveAttribute('aria-expanded', 'false');
    expect(within(panel).queryByRole('checkbox', { name: 'M' })).not.toBeInTheDocument();
  });

  it('given values chosen in a group, when it is collapsed, then its header still says them', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    renderRoute('/');

    const panel = await openFilters();
    await userEvent.click(within(await group(panel, es.filters.size)).getByRole('checkbox', { name: 'M' }));
    await userEvent.click(within(await group(panel, es.filters.size)).getByRole('checkbox', { name: 'L' }));
    await userEvent.click(toggleOf(panel, es.filters.size));

    expect(toggleOf(panel, es.filters.size)).toHaveAttribute('aria-expanded', 'false');
    expect(toggleOf(panel, es.filters.size)).toHaveAccessibleDescription('M, L');
  });

  it('given letter, number and one-size sizes, when the panel opens, then they come apart, each in natural order', async () => {
    stubCatalogApi({
      listings: [listed([product('rosa')])],
      filters: filterOptions({ sizes: ['10', '2', 'Único', 'XL', 'S', '4'] }),
    });
    renderRoute('/');

    const panel = await openFilters();
    const sizes = await group(panel, es.filters.size);

    const kinds = [...sizes.querySelectorAll('.size-kind')].map((kind) => ({
      name: kind.querySelector('.size-kind-name')?.textContent,
      sizes: within(kind as HTMLElement)
        .getAllByRole('checkbox')
        .map((box) => box.closest('label')?.textContent),
    }));
    expect(kinds).toEqual([
      { name: es.filters.sizeKinds.letters, sizes: ['S', 'XL'] },
      { name: es.filters.sizeKinds.numbers, sizes: ['2', '4', '10'] },
      { name: es.filters.sizeKinds.other, sizes: ['Único'] },
    ]);
  });

  it('given applied filters, when one chip is removed, then only that value goes and the sort stays', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])] });
    const { router } = renderRoute('/?talle=M&publico=mujer&precio-hasta=20000&orden=menor-precio');

    const applied = await screen.findByRole('group', { name: es.filters.applied });
    expect(within(applied).getAllByRole('button').map((chip) => chip.textContent)).toEqual([
      es.filters.audiences.WOMEN,
      es.filters.sizeChip('M'),
      es.filters.priceUpTo(formatMoney(2_000_000)),
      es.filters.clearAll,
    ]);
    await userEvent.click(within(applied).getByRole('button', { name: es.filters.removeChip(es.filters.sizeChip('M')) }));

    await waitFor(() => {
      expect(router.state.location.search).toBe('?publico=mujer&precio-hasta=20000&orden=menor-precio');
    });
    expect(queriesTo(get, PRODUCTS).at(-1)).toEqual({ audience: ['WOMEN'], maxPrice: 2_000_000, sort: 'PRICE_ASC', page: 0 });
  });

  it('given a chip removed, when the chips change, then the focus goes to the next chip, then to the filter bar', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    renderRoute('/?talle=M&talle=L');

    const applied = await screen.findByRole('group', { name: es.filters.applied });
    await userEvent.click(within(applied).getByRole('button', { name: es.filters.removeChip(es.filters.sizeChip('M')) }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: es.filters.removeChip(es.filters.sizeChip('L')) })).toHaveFocus();
    });
    await userEvent.keyboard('{Enter}');

    await waitFor(() => {
      expect(screen.queryByRole('group', { name: es.filters.applied })).not.toBeInTheDocument();
    });
    expect(within(screen.getByRole('group', { name: es.filters.bar })).getAllByRole('button')[0]).toHaveFocus();
  });

  it('given applied filters, when "Clear all" is pressed, then every filter goes and the sort stays', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    const { router } = renderRoute('/?talle=M&color=Rosa&orden=menor-precio');

    const applied = await screen.findByRole('group', { name: es.filters.applied });
    await userEvent.click(within(applied).getByRole('button', { name: es.filters.clearAll }));

    await waitFor(() => {
      expect(router.state.location.search).toBe('?orden=menor-precio');
    });
    expect(screen.queryByRole('group', { name: es.filters.applied })).not.toBeInTheDocument();
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

    // Open already: the pressed button's group is never collapsed.
    await waitFor(() => {
      expect(
        within(within(panel).getByRole('group', { name: es.filters.size })).getByRole('checkbox', { name: 'S' }),
      ).toHaveFocus();
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
