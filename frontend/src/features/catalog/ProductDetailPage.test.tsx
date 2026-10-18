import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import {
  answer,
  detailImage,
  detailVariant,
  NO_CART,
  NO_REVIEWS,
  productDetail,
  REVIEWS_PATH,
  withHeader,
} from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

/** Answers the product detail calls in order; the header's cart is empty and the product has no reviews. */
function stubDetail(...answers: never[]) {
  const queue: unknown[] = [...answers];
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) => {
    if (path === '/api/cart') {
      return Promise.resolve(NO_CART);
    }
    return Promise.resolve(path === REVIEWS_PATH ? NO_REVIEWS : queue.shift());
  }));
}

const addToCart = () => screen.getByRole('button', { name: es.product.addToCart });

describe('product detail (UC-03)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a listed product, when it opens, then gallery, description, material, season and price are shown', async () => {
    const get = stubDetail(answer(200, productDetail()));

    renderRoute('/producto/luna');

    expect(await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' })).toBeInTheDocument();
    expect(screen.getByText(/^\$\s25\.000$/)).toBeInTheDocument();
    expect(screen.getByText('Pijama largo de algodón.')).toBeInTheDocument();
    expect(screen.getByText('Algodón')).toBeInTheDocument();
    expect(screen.getByText(es.filters.seasons.WINTER)).toBeInTheDocument();
    const main = screen.getByRole('img', { name: 'Pijama Luna, foto 1' });
    expect(main).toHaveAttribute('src', '/media/frente-400.webp');
    expect(main).toHaveAttribute('loading', 'eager');
    expect(get).toHaveBeenCalledWith('/api/catalog/products/{slug}', expect.objectContaining({
      params: { path: { slug: 'luna' } },
    }));
  });

  it('given pink M in stock and light-blue M out of stock, when the detail opens, then only light-blue M is sold out', async () => {
    stubDetail(answer(200, productDetail()));

    renderRoute('/producto/luna');

    const colors = within(await screen.findByRole('group', { name: es.product.color }));
    expect(colors.getByRole('radio', { name: es.product.soldOutOption('Celeste') })).toBeInTheDocument();
    expect(colors.getByRole('radio', { name: 'Rosa' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'M' })).toBeChecked();
  });

  it('given both size and color picked with stock, when shown, then the exact stock shows and buying is enabled', async () => {
    stubDetail(answer(200, productDetail()));
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));

    expect(screen.getByRole('status')).toHaveTextContent(es.product.available(3));
    expect(addToCart()).toBeEnabled();
  });

  it('given only one of size and color picked, when shown, then the buy button stays disabled', async () => {
    stubDetail(answer(200, productDetail()));

    renderRoute('/producto/luna');

    expect(await screen.findByRole('radio', { name: 'M' })).toBeChecked();
    expect(addToCart()).toBeDisabled();
    expect(addToCart()).toHaveAccessibleDescription(es.product.pickSizeAndColor);
  });

  it('given a combination with available stock 0, when selected, then the buy button is disabled', async () => {
    stubDetail(answer(200, productDetail()));
    renderRoute('/producto/luna');
    await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));

    await userEvent.click(screen.getByRole('radio', { name: es.product.soldOutOption('Celeste') }));

    expect(screen.getByRole('radio', { name: es.product.soldOutOption('M') })).toBeDisabled();
    expect(addToCart()).toBeDisabled();
  });

  it('given the detail open, when the color changes, then the price follows the variant and its photos come first', async () => {
    stubDetail(answer(200, productDetail({
      variants: [detailVariant('M', 'Rosa', 3, 2_500_000), detailVariant('M', 'Celeste', 2, 2_800_000)],
    })));
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));
    expect(screen.getByText(/^\$\s25\.000$/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Pijama Luna, Rosa, foto 1' })).toHaveAttribute('src', '/media/rosa-400.webp');

    await userEvent.click(screen.getByRole('radio', { name: 'Celeste' }));
    expect(screen.getByText(/^\$\s28\.000$/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Pijama Luna, Celeste, foto 1' }))
      .toHaveAttribute('src', '/media/celeste-400.webp');
  });

  it('given several photos, when a thumbnail is chosen, then it becomes the main photo with its placeholder', async () => {
    stubDetail(answer(200, productDetail({ images: [detailImage('frente', null), detailImage('espalda', null)] })));
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('button', { name: es.product.showPhoto(2) }));

    const main = screen.getByRole('img', { name: 'Pijama Luna, foto 2' });
    expect(main).toHaveAttribute('src', '/media/espalda-400.webp');
    expect(main.closest('.ph')).not.toHaveClass('loaded');
    expect(screen.getByRole('button', { name: es.product.showPhoto(2) })).toHaveAttribute('aria-pressed', 'true');
  });

  it('given a slug that does not exist, when opened, then a 404 page with a link to the shop is shown', async () => {
    stubDetail(answer(404, { status: 404 }));

    renderRoute('/producto/no-existe');

    expect(await screen.findByRole('heading', { level: 1, name: es.notFound.title })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: es.notFound.backToStore })).toHaveAttribute('href', '/');
  });

  it('given the detail fails to load, when retried, then the product shows', async () => {
    stubDetail(answer(500), answer(200, productDetail()));
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('button', { name: es.product.retry }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' })).toBeInTheDocument();
  });
});

describe('size guide on the product page (RNF-31.5)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a category with a guide, when one of its products opens, then the guide shows the columns, the rows in order and the note', async () => {
    stubDetail(answer(200, productDetail({
      sizeGuide: {
        columns: ['Busto (cm)', 'Cintura (cm)'],
        rows: [
          { size: 'M', values: ['88–93', '70–75'] },
          { size: 'S', values: ['83–87', ''] },
        ],
        note: 'Medí sobre la ropa interior.',
      },
    })));

    renderRoute('/producto/luna');

    const summary = await screen.findByText(es.product.sizeGuide);
    await userEvent.click(summary);
    const table = within(screen.getByRole('table'));
    expect(table.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      es.product.size,
      'Busto (cm)',
      'Cintura (cm)',
    ]);
    expect(table.getAllByRole('rowheader').map((cell) => cell.textContent)).toEqual(['M', 'S']);
    const small = table.getAllByRole('row')[2];
    expect(small === undefined ? [] : within(small).getAllByRole('cell').map((cell) => cell.textContent)).toEqual([
      '83–87',
      es.product.noMeasure,
    ]);
    expect(screen.getByText('Medí sobre la ropa interior.')).toBeVisible();
  });

  it('given a category without a guide, when one of its products opens, then no size guide link is shown', async () => {
    stubDetail(answer(200, productDetail()));

    renderRoute('/producto/luna');

    expect(await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' })).toBeInTheDocument();
    expect(screen.queryByText(es.product.sizeGuide)).not.toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
