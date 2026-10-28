import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { cart, cartItem } from '../../test/cartApi.ts';
import {
  answer,
  detailImage,
  detailVariant,
  listed,
  NO_CART,
  NO_REVIEWS,
  NO_SHIPPING_RATES,
  product,
  productDetail,
  purchaseInfo,
  queriesTo,
  REVIEWS_PATH,
  SHIPPING_RATES_PATH,
  withHeader,
} from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

/** The listing of UC-01, which "Completá tu look" reads (D-08). */
const LISTING_PATH = '/api/catalog/products';

/**
 * Answers the product detail calls in order; the header's cart is empty, the product has no reviews and its category
 * no other product.
 */
function stubDetail(...answers: never[]) {
  const queue: unknown[] = [...answers];
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) => {
    if (path === '/api/cart') {
      return Promise.resolve(NO_CART);
    }
    if (path === LISTING_PATH) {
      return Promise.resolve(listed([]));
    }
    return Promise.resolve(path === REVIEWS_PATH ? NO_REVIEWS : (purchaseInfo(path) ?? queue.shift()));
  }));
}

const addToCart = () => screen.getByRole('button', { name: es.product.addToCart });

/** An amount as Testing Library reads it: the non-breaking space after the sign becomes a plain one. */
const shown = (cents: number) => formatMoney(cents).replace(/\s/g, ' ');

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
    expect(main).toHaveAttribute('fetchpriority', 'high');
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

  it('given the reviews hidden in the store, when a product with approved reviews opens, then no rating, reviews nor review form shows', async () => {
    const get = vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) => {
      if (path === '/api/cart') {
        return Promise.resolve(NO_CART);
      }
      if (path === LISTING_PATH) {
        return Promise.resolve(listed([]));
      }
      if (path === REVIEWS_PATH) {
        return Promise.resolve(answer(200, { average: 4.5, count: 2, reviews: [] }));
      }
      return Promise.resolve(purchaseInfo(path) ?? answer(200, productDetail()));
    }));

    renderRoute('/producto/luna');

    await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' });
    expect(screen.queryByRole('region', { name: es.reviews.product.title })).toBeNull();
    expect(screen.queryByRole('heading', { name: es.reviews.mine.title })).toBeNull();
    expect(queriesTo(get, REVIEWS_PATH)).toHaveLength(0);
    const data = document.querySelector('script[type="application/ld+json"]')?.textContent ?? '';
    expect(JSON.parse(data)).not.toHaveProperty('aggregateRating');
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

    const summary = await within(await screen.findByRole('main')).findByText(es.product.sizeGuide);
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
    expect(within(screen.getByRole('main')).queryByText(es.product.sizeGuide)).not.toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});

/** The product page with these shipping rates, this product and this first page of its category. */
function stubPage(rates: unknown = NO_SHIPPING_RATES, detail = productDetail(), category: unknown = listed([])) {
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) => {
    if (path === '/api/cart') {
      return Promise.resolve(NO_CART);
    }
    if (path === LISTING_PATH) {
      return Promise.resolve(category);
    }
    if (path === SHIPPING_RATES_PATH) {
      return Promise.resolve(rates);
    }
    return Promise.resolve(path === REVIEWS_PATH ? NO_REVIEWS : (purchaseInfo(path) ?? answer(200, detail)));
  }));
}

/** Replaces IntersectionObserver; the returned function tells every observer where the add button is. */
function stubIntersections() {
  const callbacks: IntersectionObserverCallback[] = [];
  vi.stubGlobal('IntersectionObserver', class {
    constructor(callback: IntersectionObserverCallback) {
      callbacks.push(callback);
    }

    observe() {
      // The test reports the intersections.
    }

    disconnect() {
      // Nothing to release.
    }
  });
  return ({ isIntersecting, top }: { isIntersecting: boolean; top: number }) => {
    act(() => {
      for (const callback of callbacks) {
        callback([{ isIntersecting, boundingClientRect: { top } }] as never, {} as never);
      }
    });
  };
}

const stickyBar = () => document.querySelector<HTMLElement>('.pdp-sticky-cta');

describe('product page as in the prototype (E7, changes to UC-03)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('given a product, when it opens, then the breadcrumbs lead home and to its category and end at the product', async () => {
    stubPage();

    renderRoute('/producto/luna');

    const crumbs = within(await screen.findByRole('navigation', { name: es.product.breadcrumbs }));
    expect(crumbs.getByRole('link', { name: es.product.home })).toHaveAttribute('href', '/');
    expect(crumbs.getByRole('link', { name: 'Pijamas largos' })).toHaveAttribute('href', '/categoria/pijamas-largos');
    expect(crumbs.getByText('Pijama Luna')).toHaveAttribute('aria-current', 'page');
    // The breadcrumbs are the only way back (UC-03).
    expect(screen.queryByRole('link', { name: 'Volver al catálogo' })).not.toBeInTheDocument();
  });

  it('given the main photo, when pressed, then the enlarged view shows its 1200 px file and closes again', async () => {
    stubPage();
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('button', { name: es.product.enlargePhoto }));

    const view = within(screen.getByRole('dialog', { name: es.product.enlarged }));
    expect(view.getByRole('img', { name: 'Pijama Luna, foto 1' })).toHaveAttribute('src', '/media/frente-1200.webp');
    await userEvent.click(view.getByRole('button', { name: es.product.closeEnlarged }));
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: es.product.enlarged })).not.toBeInTheDocument();
    });
  });

  it('given colors with a tone, when one is picked, then they show as swatches and its name shows next to "Color"', async () => {
    stubPage();
    renderRoute('/producto/luna');

    const colors = await screen.findByRole('group', { name: es.product.color });
    await userEvent.click(within(colors).getByRole('radio', { name: 'Rosa' }));

    expect(colors.querySelectorAll('.swatch')).toHaveLength(2);
    expect(within(colors).getByText(': Rosa')).toBeInTheDocument();
  });

  it('given a product page, when quantity 2 is chosen and added, then the cart gets 2 units of that variant', async () => {
    stubPage();
    const post = vi.spyOn(api, 'POST').mockResolvedValue(cart([cartItem({ quantity: 2 })]));
    renderRoute('/producto/luna');
    await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));

    await userEvent.click(screen.getByRole('button', { name: es.product.moreUnits }));
    await userEvent.click(addToCart());

    expect(post).toHaveBeenCalledWith('/api/cart/items', { body: { variantId: 'M-Rosa', quantity: 2 } });
  });

  it('given the available stock, when the quantity is raised or typed past it, then it stops at the stock', async () => {
    stubPage();
    renderRoute('/producto/luna');
    await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));
    const more = screen.getByRole('button', { name: es.product.moreUnits });

    await userEvent.click(more);
    await userEvent.click(more);

    const field = screen.getByRole('spinbutton', { name: es.product.quantity });
    expect(field).toHaveValue(3);
    expect(more).toBeDisabled();
    await userEvent.clear(field);
    await userEvent.type(field, '9');
    await userEvent.tab();
    expect(field).toHaveValue(3);
  });

  it('given a quantity above the available stock, when added, then the UC-08 stock message shows', async () => {
    stubPage();
    vi.spyOn(api, 'POST').mockResolvedValue(
      answer(409, { problem: 'INSUFFICIENT_STOCK', variantId: 'M-Rosa', available: 1 }),
    );
    renderRoute('/producto/luna');
    await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));

    await userEvent.click(screen.getByRole('button', { name: es.product.moreUnits }));
    await userEvent.click(addToCart());

    expect(await screen.findByText(es.product.onlyLeft(1))).toBeInTheDocument();
  });

  it('given a new color, when picked, then the quantity starts again at 1', async () => {
    stubPage(NO_SHIPPING_RATES, productDetail({
      variants: [detailVariant('M', 'Rosa', 3), detailVariant('M', 'Celeste', 2)],
    }));
    renderRoute('/producto/luna');
    await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));
    await userEvent.click(screen.getByRole('button', { name: es.product.moreUnits }));

    await userEvent.click(screen.getByRole('radio', { name: 'Celeste' }));

    expect(screen.getByRole('spinbutton', { name: es.product.quantity })).toHaveValue(1);
  });

  it('given a phone, when scrolled until the add button is off screen, then the fixed bar shows the price and adds as the main button', async () => {
    const intersect = stubIntersections();
    stubPage();
    const post = vi.spyOn(api, 'POST').mockResolvedValue(cart([cartItem()]));
    renderRoute('/producto/luna');
    await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));
    expect(stickyBar()).toBeNull();

    intersect({ isIntersecting: false, top: -120 });

    const bar = stickyBar();
    expect(bar).not.toBeNull();
    const sticky = within(bar ?? document.body);
    expect(sticky.getByText(/^\$\s25\.000$/)).toBeInTheDocument();
    await userEvent.click(sticky.getByRole('button', { name: es.product.addToCart }));
    expect(post).toHaveBeenCalledWith('/api/cart/items', { body: { variantId: 'M-Rosa', quantity: 1 } });
  });

  it('given the add button below the screen or back in view, when observed, then there is no fixed bar', async () => {
    const intersect = stubIntersections();
    stubPage();
    renderRoute('/producto/luna');
    await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' });

    intersect({ isIntersecting: false, top: 900 });
    expect(stickyBar()).toBeNull();

    intersect({ isIntersecting: false, top: -120 });
    expect(stickyBar()).not.toBeNull();
    intersect({ isIntersecting: true, top: 200 });
    await waitFor(() => {
      expect(stickyBar()).toBeNull();
    });
  });

  it('given provinces with and without shipping, when each is chosen, then the cost, free shipping or no shipping shows', async () => {
    stubPage(answer(200, {
      zones: [{ province: 'SANTA_FE', costCents: 0 }, { province: 'CORDOBA', costCents: 650_000 }],
      freeShippingThresholdCents: null,
    }));
    renderRoute('/producto/luna');
    const province = await screen.findByRole('combobox', { name: es.product.shipping });

    await userEvent.selectOptions(province, 'CORDOBA');
    expect(screen.getByText(es.product.shippingTo('Córdoba', shown(650_000)))).toBeInTheDocument();

    await userEvent.selectOptions(province, 'SANTA_FE');
    expect(screen.getByText(es.product.freeShippingTo('Santa Fe'))).toBeInTheDocument();

    await userEvent.selectOptions(province, 'JUJUY');
    expect(screen.getByText(es.product.noShippingTo)).toBeInTheDocument();
  });

  it('given the shipping costs fail to load, when a product opens, then there is no province select', async () => {
    stubPage(answer(500));

    renderRoute('/producto/luna');

    expect(await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' })).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: es.product.shipping })).not.toBeInTheDocument();
  });

  it('given a product, when its sections open, then the composition has material and season and the returns link to the FAQ', async () => {
    stubPage();
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('button', { name: es.product.composition }));
    expect(screen.getByText('Algodón')).toBeVisible();
    expect(screen.getByText(es.filters.seasons.WINTER)).toBeVisible();

    await userEvent.click(screen.getByRole('button', { name: es.product.returns }));
    expect(screen.getByRole('link', { name: es.product.returnsLink })).toHaveAttribute('href', '/preguntas-frecuentes');
  });
});

describe('"Completá tu look" on the product page (E7, D-08)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  /** The first page of the category, newest first, with the open product third. */
  const category = (others: number) => {
    const items = Array.from({ length: others }, (_, index) => product(`otro-${String(index)}`));
    items.splice(2, 0, product('luna'));
    return listed(items);
  };

  it('given a category with 6 other products, when a product page opens, then it shows the 4 newest without the open product', async () => {
    const get = stubPage(NO_SHIPPING_RATES, productDetail(), category(6));

    renderRoute('/producto/luna');

    const related = within(await screen.findByRole('region', { name: es.product.related }));
    expect(related.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      '/producto/otro-0',
      '/producto/otro-1',
      '/producto/otro-2',
      '/producto/otro-3',
    ]);
    expect(queriesTo(get, LISTING_PATH)).toEqual([{ category: 'pijamas-largos', page: 0 }]);
  });

  it('given related products, when a product page opens, then "Completá tu look" sits below the info column (UC-03)', async () => {
    stubPage(NO_SHIPPING_RATES, productDetail(), category(2));

    renderRoute('/producto/luna');

    const related = await screen.findByRole('region', { name: es.product.related });
    const info = screen.getByRole('heading', { level: 1, name: 'Pijama Luna' }).parentElement;
    expect(info?.contains(related)).toBe(false);
    expect(info?.compareDocumentPosition(related)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('given a product page, when it opens, then the collapsed sections come right after the add button and the shipping cost after them (UC-03)', async () => {
    stubPage(answer(200, { zones: [{ province: 'SANTA_FE', costCents: 0 }], freeShippingThresholdCents: null }));

    renderRoute('/producto/luna');

    const province = await screen.findByRole('combobox', { name: es.product.shipping });
    const add = screen.getByRole('button', { name: es.product.addToCart });
    const composition = screen.getByRole('button', { name: es.product.composition });
    expect(add.compareDocumentPosition(composition)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(composition.compareDocumentPosition(province)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('given a category with no other products, when a product page opens, then "Completá tu look" is not shown', async () => {
    const get = stubPage(NO_SHIPPING_RATES, productDetail(), category(0));

    renderRoute('/producto/luna');

    await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' });
    await waitFor(() => {
      expect(queriesTo(get, LISTING_PATH)).toHaveLength(1);
    });
    expect(screen.queryByRole('heading', { name: es.product.related })).not.toBeInTheDocument();
  });

  it('given the category listing fails, when a product page opens, then the product shows without "Completá tu look"', async () => {
    stubPage(NO_SHIPPING_RATES, productDetail(), answer(500));

    renderRoute('/producto/luna');

    expect(await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: es.product.related })).not.toBeInTheDocument();
  });
});
