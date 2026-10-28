import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import {
  answer,
  listed,
  product,
  products,
  queriesTo,
  stubCatalogApi,
  type ListItem,
} from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const PRODUCTS = '/api/catalog/products';

/** The home page's listing, apart from the other lists of the page (UC-30). */
const listing = () => within(screen.getByRole('region', { name: es.catalog.allTitle }));

describe('catalog grid (UC-01)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given listed products, when the shop opens, then each shows its first photo, name and "from" price', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    const card = await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(card).toHaveAttribute('href', '/producto/rosa');
    expect(within(card).getByText(/Desde \$\s12\.345$/)).toBeInTheDocument();
    const photo = within(card).getByRole('img', { name: 'Pijama rosa' });
    expect(photo).toHaveAttribute('src', '/media/rosa-400.webp');
    expect(photo).toHaveAttribute(
      'srcset',
      '/media/rosa-400.webp 400w, /media/rosa-800.webp 800w, /media/rosa-1200.webp 1200w',
    );
    expect(queriesTo(get, PRODUCTS)).toEqual([{ page: 0 }]);
  });

  it('given a product whose variants are all out of stock, when listed, then it appears with "Sold out"', async () => {
    stubCatalogApi({ listings: [listed([product('agotado', { soldOut: true }), product('con-stock')])] });

    renderRoute('/');

    const soldOut = await screen.findByRole('link', { name: /Pijama agotado/ });
    expect(within(soldOut).getByText(es.catalog.soldOut)).toBeInTheDocument();
    expect(within(screen.getByRole('link', { name: /Pijama con-stock/ })).queryByText(es.catalog.soldOut))
      .not.toBeInTheDocument();
  });

  it('given a product with stock only in pink, when listed, then its card shows only the pink swatch (D-04)', async () => {
    stubCatalogApi({ listings: [listed([product('rosa', { colors: ['Rosa'] }), product('agotado', { soldOut: true })])] });

    renderRoute('/');

    const card = await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(within(card).getAllByTestId('swatch')).toHaveLength(1);
    expect(within(card).getByTestId('swatch')).toHaveAttribute('title', 'Rosa');
    expect(within(card).getByText('Color: Rosa')).toBeInTheDocument();
    expect(within(screen.getByRole('link', { name: /Pijama agotado/ })).queryByTestId('swatch')).not.toBeInTheDocument();
  });

  it('given 7 colors, one unknown to the map, when listed, then 5 show, the unknown one by name, and "+2"', async () => {
    const colors = ['Rosa', 'Unicornio', 'Beige', 'Salvia', 'Negro', 'Celeste', 'Lila'];
    stubCatalogApi({ listings: [listed([product('muchos', { colors })])] });

    renderRoute('/');

    const card = await screen.findByRole('link', { name: /Pijama muchos/ });
    expect(within(card).getAllByTestId('swatch').map((swatch) => swatch.getAttribute('title')))
      .toEqual(['Rosa', 'Beige', 'Salvia', 'Negro']);
    expect(within(card).getByText('Unicornio')).toHaveAttribute('aria-hidden', 'true');
    expect(within(card).getByText('+2')).toBeInTheDocument();
    expect(within(card).getByText('Colores: Rosa, Unicornio, Beige, Salvia, Negro, Celeste y Lila'))
      .toBeInTheDocument();
  });

  it('given a new and an older product, when listed, then only the new one says "Nuevo" (D-05)', async () => {
    stubCatalogApi({ listings: [listed([product('nuevo', { isNew: true }), product('viejo')])] });

    renderRoute('/');

    const fresh = await screen.findByRole('link', { name: /Pijama nuevo/ });
    expect(within(fresh).getByText(es.catalog.isNew)).toBeInTheDocument();
    expect(within(screen.getByRole('link', { name: /Pijama viejo/ })).queryByText(es.catalog.isNew))
      .not.toBeInTheDocument();
  });

  it('given 30 matches, when the listing opens, then it reads "30 productos" with 24 cards and "Ver más" (D-11)', async () => {
    stubCatalogApi({ listings: [listed(products(24, 'p'), true, 30), listed(products(6, 'q'), false, 30)] });

    renderRoute('/');

    expect(await listing().findAllByRole('listitem')).toHaveLength(24);
    expect(listing().getByRole('status')).toHaveTextContent('30 productos');
    await userEvent.click(screen.getByRole('button', { name: es.catalog.showMore }));
    await waitFor(() => {
      expect(listing().getAllByRole('listitem')).toHaveLength(30);
    });
    expect(listing().getByRole('status')).toHaveTextContent('30 productos');
  });

  it('given matches, when the listing opens, then the count ends the filter bar row, after the buttons (T-E7-11)', async () => {
    stubCatalogApi({ listings: [listed(products(3, 'p'), false, 3)] });

    renderRoute('/');

    const count = await listing().findByText('3 productos');
    const bar = listing().getByRole('group', { name: es.filters.bar });
    expect(count.parentElement).toBe(bar.parentElement);
    expect(bar.compareDocumentPosition(count) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('given one match, when the listing opens, then it reads "1 producto"', async () => {
    stubCatalogApi({ listings: [listed([product('solo')])] });

    renderRoute('/categoria/verano');

    expect(await screen.findByText('1 producto')).toHaveAttribute('role', 'status');
  });

  it('given a category listing, when it renders, then the first four photos load eagerly and the first two first (T-E6-02)', async () => {
    stubCatalogApi({ listings: [listed(products(6, 'p'))] });

    renderRoute('/categoria/verano');

    await within(screen.getByRole('main')).findAllByRole('listitem');
    const photos = screen.getAllByTestId('blurred-image').map((image) => within(image).getByRole('img'));
    expect(photos.map((photo) => photo.getAttribute('loading'))).toEqual(
      ['eager', 'eager', 'eager', 'eager', 'lazy', 'lazy'],
    );
    expect(photos.map((photo) => photo.getAttribute('fetchpriority'))).toEqual(
      ['high', 'high', 'auto', 'auto', 'auto', 'auto'],
    );
  });

  it('given more than 24 matches, when the catalog opens, then 24 are shown and a button to show more', async () => {
    const get = stubCatalogApi({ listings: [listed(products(24, 'first'), true), listed(products(3, 'second'))] });
    renderRoute('/');

    expect(await listing().findAllByRole('listitem')).toHaveLength(24);
    await userEvent.click(screen.getByRole('button', { name: es.catalog.showMore }));

    await waitFor(() => {
      expect(listing().getAllByRole('listitem')).toHaveLength(27);
    });
    expect(screen.queryByRole('button', { name: es.catalog.showMore })).not.toBeInTheDocument();
    expect(queriesTo(get, PRODUCTS)).toEqual([{ page: 0 }, { page: 1 }]);
  });

  it('given a failure loading more, when "Show more" is pressed, then the loaded products stay and it can retry', async () => {
    stubCatalogApi({ listings: [listed(products(24, 'first'), true), answer(503)] });
    renderRoute('/');

    await userEvent.click(await screen.findByRole('button', { name: es.catalog.showMore }));

    expect(await screen.findByText(es.catalog.loadMoreError)).toBeInTheDocument();
    expect(listing().getAllByRole('listitem')).toHaveLength(24);
    expect(screen.getByRole('button', { name: es.catalog.showMore })).toBeEnabled();
  });

  it('given a category, when it opens, then only its products are requested', async () => {
    const get = stubCatalogApi({ listings: [listed([product('invierno')])] });

    renderRoute('/categoria/pijamas-de-invierno');

    expect(await screen.findByRole('link', { name: /Pijama invierno/ })).toBeInTheDocument();
    expect(queriesTo(get, PRODUCTS)).toEqual([{ category: 'pijamas-de-invierno', page: 0 }]);
    expect(queriesTo(get, '/api/catalog/filters')).toEqual([{ category: 'pijamas-de-invierno' }]);
  });

  it('given a category without products, when it opens, then a message links to the full catalog', async () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/categoria/vacia');

    expect(await screen.findByText(es.catalog.emptyCategory)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: es.catalog.seeAll })).toHaveAttribute('href', '/');
  });

  it('given an unknown or inactive category, when it opens, then the same message is shown', async () => {
    stubCatalogApi({ listings: [answer(404, { status: 404 })], filters: answer(404, { status: 404 }) });

    renderRoute('/categoria/no-existe');

    expect(await screen.findByText(es.catalog.emptyCategory)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: es.catalog.seeAll })).toHaveAttribute('href', '/');
    await waitFor(() => {
      expect(screen.queryByRole('group', { name: es.filters.bar })).not.toBeInTheDocument();
    });
  });

  it('given a product without photos, when listed, then its card shows no photo', async () => {
    stubCatalogApi({ listings: [listed([product('sin-foto', { image: null } as unknown as Partial<ListItem>)])] });

    renderRoute('/');

    const card = await screen.findByRole('link', { name: /Pijama sin-foto/ });
    expect(within(card).queryByRole('img')).not.toBeInTheDocument();
    expect(within(card).getByTestId('no-photo')).toBeInTheDocument();
  });

  it('given a product with two photos, when listed, then its card holds the second one, lazy and decorative (T-E7-14)', async () => {
    const second = { ...product('segunda').image, files: [{ width: 400, url: '/media/rosa-2-400.webp' }] };
    stubCatalogApi({ listings: [listed([product('rosa', { secondImage: second }), product('una-foto')])] });

    renderRoute('/');

    const card = await screen.findByRole('link', { name: /Pijama rosa/ });
    const photo = within(card).getByTestId('second-photo');
    expect(photo).toHaveAttribute('src', '/media/rosa-2-400.webp');
    expect(photo).toHaveAttribute('alt', '');
    expect(photo).toHaveAttribute('loading', 'lazy');
    expect(within(card).getAllByRole('img')).toHaveLength(1);
    const single = screen.getByRole('link', { name: /Pijama una-foto/ });
    expect(within(single).queryByTestId('second-photo')).not.toBeInTheDocument();
  });

  it('given sizes S, M and L with M out of stock, when listed, then the card shows all three with M struck through (T-E7-35)', async () => {
    const sizes = [
      { size: 'S', inStock: true },
      { size: 'M', inStock: false },
      { size: 'L', inStock: true },
    ];
    stubCatalogApi({ listings: [listed([product('rosa', { sizes })])] });

    renderRoute('/');

    const card = await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(card).toHaveAccessibleName(expect.stringContaining(es.catalog.sizes(['S', 'L'])) as string);
    const shown = card.querySelectorAll('.card-sizes span[aria-hidden]');
    expect([...shown].map((size) => [size.textContent, size.classList.contains('out')])).toEqual([
      ['S', false],
      ['M', true],
      ['L', false],
    ]);
  });

  it('given a phone and a product with two photos, when its photo is swiped, then the second shows and it does not open (T-E7-32)', async () => {
    // jsdom has no pointer capture.
    HTMLElement.prototype.setPointerCapture = vi.fn();
    const second = { files: [{ width: 400, url: '/media/rosa-2-400.webp' }] };
    stubCatalogApi({ listings: [listed([product('rosa', { secondImage: second })])] });
    const { router } = renderRoute('/');
    const card = await screen.findByRole('link', { name: /Pijama rosa/ });
    const photo = within(card).getByRole('img');
    expect(within(card).getByTestId('second-photo')).not.toHaveClass('touched');

    fireEvent.pointerDown(photo, { pointerId: 1, pointerType: 'touch', clientX: 200, clientY: 100 });
    fireEvent.pointerMove(photo, { pointerId: 1, pointerType: 'touch', clientX: 140, clientY: 102 });
    fireEvent.pointerUp(photo, { pointerId: 1, pointerType: 'touch', clientX: 90, clientY: 102 });
    fireEvent.click(photo);

    // Requested only now that a finger touched it
    expect(within(card).getByTestId('second-photo')).toHaveClass('touched');
    expect(within(card).getByTestId('photo-dots').children[1]).toHaveClass('on');
    expect(router.state.location.pathname).toBe('/');

    fireEvent.click(photo);
    expect(router.state.location.pathname).toBe('/producto/rosa');
  });

  it('given the listing fails, when the shop opens, then an error offers to retry', async () => {
    stubCatalogApi({ listings: [answer(500), listed([product('rosa')])] });
    renderRoute('/');

    await userEvent.click(await screen.findByRole('button', { name: es.catalog.retry }));

    expect(await screen.findByRole('link', { name: /Pijama rosa/ })).toBeInTheDocument();
  });
});

describe('grid or list view (T-E7-06, changes to UC-01)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  const toggle = () => within(screen.getByRole('group', { name: es.catalog.view }));

  it('given the grid, when "list" is chosen, then the products show as a list and the URL does not change', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    const { router } = renderRoute('/categoria/pijamas?talle=M');
    const card = await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(toggle().getByRole('button', { name: es.catalog.gridView })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(toggle().getByRole('button', { name: es.catalog.listView }));

    expect(toggle().getByRole('button', { name: es.catalog.listView })).toHaveAttribute('aria-pressed', 'true');
    expect(toggle().getByRole('button', { name: es.catalog.gridView })).toHaveAttribute('aria-pressed', 'false');
    expect(card.closest('ul')).toHaveClass('list-view');
    expect(router.state.location.pathname + router.state.location.search).toBe('/categoria/pijamas?talle=M');
  });

  it('given a product with two photos, when the list is chosen, then its card drops the second one (T-E7-14)', async () => {
    const second = { files: [{ width: 400, url: '/media/rosa-2-400.webp' }] };
    stubCatalogApi({ listings: [listed([product('rosa', { secondImage: second })])] });
    renderRoute('/categoria/pijamas');
    const card = await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(within(card).getByTestId('second-photo')).toBeInTheDocument();

    await userEvent.click(toggle().getByRole('button', { name: es.catalog.listView }));

    expect(within(card).queryByTestId('second-photo')).not.toBeInTheDocument();
  });

  it('given the list chosen, when another listing opens in the same visit, then it shows as a list too', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')]), listed([product('rosa')])] });
    const first = renderRoute('/categoria/pijamas');
    await userEvent.click(await screen.findByRole('button', { name: es.catalog.listView }));
    first.unmount();

    renderRoute('/');

    const card = await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(card.closest('ul')).toHaveClass('list-view');
    expect(toggle().getByRole('button', { name: es.catalog.listView })).toHaveAttribute('aria-pressed', 'true');
  });
});
