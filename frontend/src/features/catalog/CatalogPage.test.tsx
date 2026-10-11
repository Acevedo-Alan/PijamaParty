import { screen, waitFor, within } from '@testing-library/react';
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

  it('given more than 24 matches, when the catalog opens, then 24 are shown and a button to show more', async () => {
    const get = stubCatalogApi({ listings: [listed(products(24, 'first'), true), listed(products(3, 'second'))] });
    renderRoute('/');

    expect(await screen.findAllByRole('listitem')).toHaveLength(24);
    await userEvent.click(screen.getByRole('button', { name: es.catalog.showMore }));

    await waitFor(() => {
      expect(screen.getAllByRole('listitem')).toHaveLength(27);
    });
    expect(screen.queryByRole('button', { name: es.catalog.showMore })).not.toBeInTheDocument();
    expect(queriesTo(get, PRODUCTS)).toEqual([{ page: 0 }, { page: 1 }]);
  });

  it('given a failure loading more, when "Show more" is pressed, then the loaded products stay and it can retry', async () => {
    stubCatalogApi({ listings: [listed(products(24, 'first'), true), answer(503)] });
    renderRoute('/');

    await userEvent.click(await screen.findByRole('button', { name: es.catalog.showMore }));

    expect(await screen.findByText(es.catalog.loadMoreError)).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(24);
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
      expect(screen.queryByRole('button', { name: es.filters.open })).not.toBeInTheDocument();
    });
  });

  it('given a product without photos, when listed, then its card shows no photo', async () => {
    stubCatalogApi({ listings: [listed([product('sin-foto', { image: null } as unknown as Partial<ListItem>)])] });

    renderRoute('/');

    const card = await screen.findByRole('link', { name: /Pijama sin-foto/ });
    expect(within(card).queryByRole('img')).not.toBeInTheDocument();
    expect(within(card).getByTestId('no-photo')).toBeInTheDocument();
  });

  it('given the listing fails, when the shop opens, then an error offers to retry', async () => {
    stubCatalogApi({ listings: [answer(500), listed([product('rosa')])] });
    renderRoute('/');

    await userEvent.click(await screen.findByRole('button', { name: es.catalog.retry }));

    expect(await screen.findByRole('link', { name: /Pijama rosa/ })).toBeInTheDocument();
  });
});
