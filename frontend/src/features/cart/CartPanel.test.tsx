import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { cart, cartItem } from '../../test/cartApi.ts';
import { answer, NO_CART, productDetail } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

/** The product page of the pink M pijama (3 in stock), with this cart in the header. */
function stubStore(currentCart: unknown = NO_CART) {
  return vi.spyOn(api, 'GET').mockImplementation(((path: string) =>
    Promise.resolve(path === '/api/cart' ? currentCart : answer(200, productDetail()))) as never);
}

function stubAdd(next: never) {
  return vi.spyOn(api, 'POST').mockResolvedValue(next);
}

async function addPinkM() {
  await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));
  await userEvent.click(screen.getByRole('button', { name: es.product.addToCart }));
}

const panel = () => screen.getByRole('dialog', { name: es.cart.title });

describe('cart side panel (UC-08)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    Reflect.deleteProperty(navigator, 'vibrate');
  });

  it('given a successful add, when it finishes, then the side panel opens by itself and the counter updates', async () => {
    stubStore();
    const post = stubAdd(cart([cartItem()]));
    const vibrate = vi.fn(() => true);
    Object.defineProperty(navigator, 'vibrate', { value: vibrate, configurable: true });
    renderRoute('/producto/luna');
    expect(await screen.findByRole('button', { name: es.cart.open(0) })).toBeInTheDocument();

    await addPinkM();

    expect(post).toHaveBeenCalledWith('/api/cart/items', { body: { variantId: 'M-Rosa', quantity: 1 } });
    const opened = within(await screen.findByRole('dialog', { name: es.cart.title }));
    expect(opened.getByRole('link', { name: 'Pijama Luna' })).toHaveAttribute('href', '/producto/luna');
    expect(opened.getByText(es.cart.variant('Rosa', 'M'))).toBeInTheDocument();
    expect(opened.getByText(es.cart.quantity(1))).toBeInTheDocument();
    expect(opened.getByRole('img', { name: es.cart.photoAlt('Pijama Luna', 'Rosa') }))
      .toHaveAttribute('src', '/uploads/luna-rosa-400.webp');
    expect(opened.getByText(es.cart.total).nextElementSibling).toHaveTextContent(/^\$\s25\.000$/);
    expect(screen.getByRole('button', { name: es.cart.open(1), hidden: true })).toBeInTheDocument();
    expect(vibrate).toHaveBeenCalledWith(18);
    expect(screen.getByRole('alert', { hidden: true })).toBeEmptyDOMElement();
  });

  it('given a device without vibration support, when adding, then the rest of the feedback works the same', async () => {
    stubStore();
    stubAdd(cart([cartItem({ quantity: 2 })]));
    renderRoute('/producto/luna');

    await addPinkM();

    expect(await screen.findByRole('dialog', { name: es.cart.title })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: es.cart.open(2), hidden: true })).toBeInTheDocument();
  });

  it('given a variant with stock 3 and 2 units in the cart, when 2 more do not fit, then "Solo quedan 3" is shown', async () => {
    const get = stubStore();
    stubAdd(answer(409, { problem: 'INSUFFICIENT_STOCK', variantId: 'M-Rosa', available: 3 }));
    renderRoute('/producto/luna');

    await addPinkM();

    expect(await screen.findByText(es.product.onlyLeft(3))).toHaveAttribute('role', 'alert');
    expect(es.product.onlyLeft(3)).toBe('Solo quedan 3');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(get.mock.calls.filter((call) => call[0] === '/api/catalog/products/{slug}')).toHaveLength(2);
  });

  it('given a variant the shop no longer sells, when adding it, then the page says so and the panel stays closed', async () => {
    stubStore();
    stubAdd(answer(404, { title: 'Not found' }));
    renderRoute('/producto/luna');

    await addPinkM();

    expect(await screen.findByText(es.product.noLongerAvailable)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('given a failed request, when adding, then the page asks to try again', async () => {
    stubStore();
    vi.spyOn(api, 'POST').mockRejectedValue(new TypeError('Failed to fetch'));
    renderRoute('/producto/luna');

    await addPinkM();

    expect(await screen.findByText(es.product.addError)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('given a cart, when the header button is tapped, then the panel shows it and closing gives the focus back', async () => {
    stubStore(cart([cartItem({ quantity: 2 })], 500_000));
    renderRoute('/producto/luna');
    const button = await screen.findByRole('button', { name: es.cart.open(2) });

    await userEvent.click(button);

    const opened = within(panel());
    expect(opened.getByText(es.cart.subtotal).nextElementSibling).toHaveTextContent(/^\$\s50\.000$/);
    expect(opened.getByText(es.cart.discount).nextElementSibling).toHaveTextContent(/^−\$\s5\.000$/);
    expect(opened.getByText(es.cart.total).nextElementSibling).toHaveTextContent(/^\$\s45\.000$/);
    await userEvent.click(opened.getByRole('button', { name: es.cart.close }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(button).toHaveFocus();
  });

  it('given an empty cart, when the panel opens, then it says so and offers to keep shopping', async () => {
    stubStore();
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('button', { name: es.cart.open(0) }));

    expect(within(panel()).getByText(es.cart.empty)).toBeInTheDocument();
    await userEvent.click(within(panel()).getByRole('button', { name: es.cart.keepShopping }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('given an item the catalog no longer has, when the panel opens, then it still shows its line', async () => {
    // The server sends null; the generated type cannot say so for a nested object.
    stubStore(cart([cartItem({ product: null as never, status: 'UNAVAILABLE', available: 0 })]));
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('button', { name: es.cart.open(1) }));

    expect(within(panel()).getByText(es.cart.unknownProduct)).toBeInTheDocument();
    expect(within(panel()).getByText(es.cart.quantity(1))).toBeInTheDocument();
  });
});
