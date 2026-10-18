import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { cart, cartItem } from '../../test/cartApi.ts';
import { answer, NO_CART, productDetail, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

/** The product page of the pink M pijama (3 in stock), with this cart in the header. */
function stubStore(currentCart: unknown = NO_CART) {
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) =>
    Promise.resolve(path === '/api/cart' ? currentCart : answer(200, productDetail()))));
}

function stubAdd(next: never) {
  return vi.spyOn(api, 'POST').mockResolvedValue(next);
}

async function addPinkM() {
  await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));
  await userEvent.click(screen.getByRole('button', { name: es.product.addToCart }));
}

const panel = () => screen.getByRole('dialog', { name: es.cart.title });

function nth(elements: HTMLElement[], index: number): HTMLElement {
  const element = elements[index];
  if (element === undefined) {
    throw new Error(`No element at ${String(index)}`);
  }
  return element;
}

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
    expect(opened.getByRole('group', { name: es.cart.quantityOf('Pijama Luna') })).toHaveTextContent('1');
    expect(opened.getByRole('img', { name: es.cart.photoAlt('Pijama Luna', 'Rosa') }))
      .toHaveAttribute('src', '/uploads/luna-rosa-400.webp');
    expect(opened.getByText(es.cart.total).nextElementSibling).toHaveTextContent(/^\$\s25\.000$/);
    expect(screen.getByRole('button', { name: es.cart.open(1), hidden: true })).toBeInTheDocument();
    expect(vibrate).toHaveBeenCalledWith(18);
    for (const alert of screen.getAllByRole('alert', { hidden: true })) {
      expect(alert).toBeEmptyDOMElement();
    }
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
    expect(within(panel()).getByText(es.cart.soldOut)).toBeInTheDocument();
    expect(within(panel()).getByRole('group', { name: es.cart.quantityOf(es.cart.unknownProduct) }))
      .toHaveTextContent('1');
  });
});

/** Opens the panel from the header; GET /api/cart answers these carts in order, and the last one repeats. */
async function openWith(...carts: unknown[]) {
  const queue = [...carts];
  const get = vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) => {
    if (path !== '/api/cart') {
      return Promise.resolve(answer(200, productDetail()));
    }
    return Promise.resolve(queue.length > 1 ? queue.shift() : queue[0]);
  }));
  renderRoute('/producto/luna');
  await userEvent.click(await screen.findByRole('button', { name: /^Carrito/ }));
  return get;
}

const cartReads = (get: { mock: { calls: unknown[][] } }) => get.mock.calls.filter((call) => call[0] === '/api/cart');

describe('cart side panel changes (UC-09)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given an item in the cart, when its quantity is changed to 0, then it is removed', async () => {
    await openWith(cart([cartItem()]));
    const patch = vi.spyOn(api, 'PATCH').mockResolvedValue(cart([]));

    await userEvent.click(within(panel()).getByRole('button', { name: es.cart.decrease('Pijama Luna') }));

    expect(patch).toHaveBeenCalledWith('/api/cart/items/{id}', {
      params: { path: { id: 'item-1' } },
      body: { quantity: 0 },
    });
    expect(await within(panel()).findByText(es.cart.empty)).toBeInTheDocument();
    expect(within(panel()).getByRole('button', { name: es.cart.close })).toHaveFocus();
    expect(screen.getByRole('button', { name: es.cart.open(0), hidden: true })).toBeInTheDocument();
  });

  it('given an item, when "+" is tapped, then the panel and the counter show the cart of the answer', async () => {
    await openWith(cart([cartItem()]));
    const patch = vi.spyOn(api, 'PATCH').mockResolvedValue(cart([cartItem({ quantity: 2 })]));

    await userEvent.click(within(panel()).getByRole('button', { name: es.cart.increase('Pijama Luna') }));

    expect(patch).toHaveBeenCalledWith('/api/cart/items/{id}', {
      params: { path: { id: 'item-1' } },
      body: { quantity: 2 },
    });
    expect(await screen.findByRole('button', { name: es.cart.open(2), hidden: true })).toBeInTheDocument();
    expect(within(panel()).getByText(es.cart.total).nextElementSibling).toHaveTextContent(/^\$\s50\.000$/);
  });

  it('given an item at its available stock, when the panel opens, then "+" is disabled', async () => {
    await openWith(cart([cartItem({ quantity: 3, available: 3 })]));

    expect(within(panel()).getByRole('button', { name: es.cart.increase('Pijama Luna') })).toBeDisabled();
    expect(within(panel()).getByRole('button', { name: es.cart.decrease('Pijama Luna') })).toBeEnabled();
  });

  it('given stock sold elsewhere, when "+" gets a 409, then the item says how many are left and the cart reloads', async () => {
    const get = await openWith(cart([cartItem({ quantity: 2 })]), cart([cartItem({ quantity: 2, available: 2 })]));
    vi.spyOn(api, 'PATCH').mockResolvedValue(
      answer(409, { problem: 'INSUFFICIENT_STOCK', variantId: 'M-Rosa', available: 2 }),
    );

    await userEvent.click(within(panel()).getByRole('button', { name: es.cart.increase('Pijama Luna') }));

    expect(await within(panel()).findByText(es.product.onlyLeft(2))).toHaveAttribute('role', 'alert');
    await vi.waitFor(() => {
      expect(cartReads(get)).toHaveLength(2);
    });
    await vi.waitFor(() => {
      expect(within(panel()).getByRole('button', { name: es.cart.increase('Pijama Luna') })).toBeDisabled();
    });
  });

  it('given an item removed in another tab, when it is changed, then the cart is read again', async () => {
    const get = await openWith(cart([cartItem()]), cart([]));
    vi.spyOn(api, 'PATCH').mockResolvedValue(answer(404, { title: 'Not found' }));

    await userEvent.click(within(panel()).getByRole('button', { name: es.cart.increase('Pijama Luna') }));

    expect(await within(panel()).findByText(es.cart.empty)).toBeInTheDocument();
    expect(cartReads(get)).toHaveLength(2);
  });

  it('given an item, when "Quitar" is tapped, then it is deleted', async () => {
    await openWith(cart([cartItem(), cartItem({ id: 'item-2', variantId: 'L-Rosa' })]));
    const remove = vi.spyOn(api, 'DELETE').mockResolvedValue(cart([cartItem()]));

    const buttons = within(panel()).getAllByRole('button', { name: es.cart.removeLabel('Pijama Luna') });
    await userEvent.click(nth(buttons, 1));

    expect(remove).toHaveBeenCalledWith('/api/cart/items/{id}', { params: { path: { id: 'item-2' } } });
    await vi.waitFor(() => {
      expect(within(panel()).getAllByRole('listitem')).toHaveLength(1);
    });
  });

  it('given a failed request, when an item is changed, then the panel asks to try again', async () => {
    await openWith(cart([cartItem()]));
    vi.spyOn(api, 'PATCH').mockRejectedValue(new TypeError('Failed to fetch'));

    await userEvent.click(within(panel()).getByRole('button', { name: es.cart.increase('Pijama Luna') }));

    expect(await within(panel()).findByText(es.cart.changeError)).toHaveAttribute('role', 'alert');
  });

  it('given a variant that sold out while in the cart, when the cart opens, then it is shown as unavailable', async () => {
    await openWith(cart([cartItem({ quantity: 2, status: 'UNAVAILABLE', available: 0 }), cartItem({ id: 'item-2' })]));

    const soldOut = nth(within(panel()).getAllByRole('listitem'), 0);
    expect(soldOut).toHaveClass('unavailable');
    expect(within(soldOut).getByText(es.cart.soldOut)).toBeInTheDocument();
    expect(within(soldOut).queryByText(/\$/)).not.toBeInTheDocument();
    expect(within(soldOut).getByRole('button', { name: es.cart.increase('Pijama Luna') })).toBeDisabled();
    expect(within(soldOut).getByRole('button', { name: es.cart.decrease('Pijama Luna') })).toBeEnabled();
    expect(within(panel()).getByText(es.cart.total).nextElementSibling).toHaveTextContent(/^\$\s25\.000$/);
  });

  it('given fewer units left than in the cart, when the cart opens, then the item asks to lower the quantity', async () => {
    await openWith(cart([cartItem({ quantity: 3, status: 'INSUFFICIENT_STOCK', available: 1 })]));

    expect(within(panel()).getByText(`${es.product.onlyLeft(1)}. ${es.cart.lowerToBuy}`)).toBeInTheDocument();
    expect(within(panel()).getByRole('button', { name: es.cart.increase('Pijama Luna') })).toBeDisabled();
  });

  it('given a price change in the catalog, when the cart opens, then the total uses the new price and it is announced', async () => {
    await openWith(cart([cartItem({ unitPriceCents: 3_000_000, previousUnitPriceCents: 2_500_000 })]));
    const post = vi.spyOn(api, 'POST').mockResolvedValue(cart([cartItem({ unitPriceCents: 3_000_000 })]));

    expect(within(panel()).getByRole('deletion')).toHaveTextContent(/^\$\s25\.000$/);
    expect(within(panel()).getByText(es.cart.total).nextElementSibling).toHaveTextContent(/^\$\s30\.000$/);
    expect(within(panel()).getByText(es.cart.pricesChanged)).toBeInTheDocument();

    await userEvent.click(within(panel()).getByRole('button', { name: es.cart.acknowledge }));

    expect(post).toHaveBeenCalledWith('/api/cart/acknowledge-prices');
    await vi.waitFor(() => {
      expect(within(panel()).queryByText(es.cart.pricesChanged)).not.toBeInTheDocument();
    });
    expect(within(panel()).queryByRole('deletion')).not.toBeInTheDocument();
  });
});

describe('cart side panel coupon (UC-10)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  /** A $20,000 cart: one pijama at ARS 20,000. */
  const item = () => cartItem({ unitPriceCents: 2_000_000 });
  const pijama10 = { code: 'PIJAMA10', status: 'APPLICABLE', missingCents: 0 } as const;
  const codeField = () => within(panel()).getByRole('textbox', { name: es.cart.couponCode });
  const applyButton = () => within(panel()).getByRole('button', { name: es.cart.applyCoupon });

  async function applyCode(code: string) {
    await userEvent.type(codeField(), code);
    await userEvent.click(applyButton());
  }

  it('given a valid 10% coupon, when applied to a $20,000 cart, then the discount is $2,000', async () => {
    await openWith(cart([item()]));
    const post = vi.spyOn(api, 'POST').mockResolvedValue(cart([item()], 200_000, pijama10));

    await applyCode('pijama10');

    expect(post).toHaveBeenCalledWith('/api/cart/coupon', { body: { code: 'pijama10' } });
    const discount = await within(panel()).findByText(es.cart.discountWith('PIJAMA10'));
    expect(discount.nextElementSibling).toHaveTextContent(/^−\$\s2\.000$/);
    expect(within(panel()).getByText(es.cart.total).nextElementSibling).toHaveTextContent(/^\$\s18\.000$/);
    expect(within(panel()).getByText(es.cart.coupon('PIJAMA10'))).toBeInTheDocument();
    expect(codeField()).toHaveValue('');
  });

  it('given a blank field, when the panel opens, then "Aplicar" is disabled', async () => {
    await openWith(cart([item()]));

    expect(applyButton()).toBeDisabled();
    await userEvent.type(codeField(), '   ');
    expect(applyButton()).toBeDisabled();
  });

  it('given an expired coupon, when applied, then "El cupón venció" is shown until the code is edited', async () => {
    await openWith(cart([item()]));
    vi.spyOn(api, 'POST').mockResolvedValue(answer(422, { problem: 'COUPON_EXPIRED' }));

    await applyCode('verano');

    expect(await within(panel()).findByText(es.cart.couponRefused.EXPIRED)).toHaveAttribute('role', 'alert');
    expect(es.cart.couponRefused.EXPIRED).toBe('El cupón venció.');
    expect(codeField()).toHaveValue('verano');
    expect(codeField()).toHaveAttribute('aria-invalid', 'true');

    await userEvent.type(codeField(), '2');

    expect(within(panel()).queryByText(es.cart.couponRefused.EXPIRED)).not.toBeInTheDocument();
  });

  it('given a cart below the minimum, when applied, then the user is told how much is missing', async () => {
    await openWith(cart([item()]));
    vi.spyOn(api, 'POST').mockResolvedValue(answer(422, { problem: 'COUPON_BELOW_MINIMUM', missingCents: 300_000 }));

    await applyCode('grande');

    expect(await within(panel()).findByText(/^Te faltan \$\s3\.000 para usar este cupón\.$/)).toHaveAttribute(
      'role',
      'alert',
    );
  });

  it.each([
    ['COUPON_UNKNOWN', es.cart.couponRefused.UNKNOWN],
    ['COUPON_INACTIVE', es.cart.couponRefused.INACTIVE],
    ['COUPON_NOT_STARTED', es.cart.couponRefused.NOT_STARTED],
    ['COUPON_USED_UP', es.cart.couponRefused.USED_UP],
  ])('given a refused code (%s), when applied, then it is rejected saying why', async (problem, message) => {
    await openWith(cart([item()]));
    vi.spyOn(api, 'POST').mockResolvedValue(answer(422, { problem }));

    await applyCode('codigo');

    expect(await within(panel()).findByText(message)).toHaveAttribute('role', 'alert');
    expect(within(panel()).queryByText(es.cart.discount)).not.toBeInTheDocument();
  });

  it('given a coupon already applied, when another is applied, then it replaces it', async () => {
    await openWith(cart([item()], 200_000, pijama10));
    vi.spyOn(api, 'POST').mockResolvedValue(
      cart([item()], 500_000, { code: 'FIJO5000', status: 'APPLICABLE', missingCents: 0 }),
    );

    await applyCode('fijo5000');

    expect(await within(panel()).findByText(es.cart.coupon('FIJO5000'))).toBeInTheDocument();
    expect(within(panel()).queryByText(es.cart.coupon('PIJAMA10'))).not.toBeInTheDocument();
    expect(within(panel()).getByText(es.cart.total).nextElementSibling).toHaveTextContent(/^\$\s15\.000$/);
  });

  it('given an applied coupon that stopped qualifying, when the cart opens, then it says why and discounts nothing', async () => {
    await openWith(cart([item()], 0, { code: 'GRANDE', status: 'BELOW_MINIMUM', missingCents: 100_000 }));

    expect(within(panel()).getByText(es.cart.coupon('GRANDE'))).toBeInTheDocument();
    expect(within(panel()).getByText(/^Te faltan \$\s1\.000 para usar este cupón\.$/)).toBeInTheDocument();
    expect(within(panel()).queryByText(es.cart.discountWith('GRANDE'))).not.toBeInTheDocument();
    expect(within(panel()).getByText(es.cart.total).nextElementSibling).toHaveTextContent(/^\$\s20\.000$/);
  });

  it('given an automatic coupon that applies (UC-23), when the cart opens, then its code labels the discount', async () => {
    await openWith(cart([item()], 200_000, null, { code: 'VERANO10', automatic: true }));

    expect(within(panel()).getByText(es.cart.discountWith('VERANO10')).nextElementSibling).toHaveTextContent(
      /^−\$\s2\.000$/,
    );
    expect(within(panel()).queryByText(es.cart.coupon('VERANO10'))).not.toBeInTheDocument();
    expect(within(panel()).getByText(es.cart.total).nextElementSibling).toHaveTextContent(/^\$\s18\.000$/);
  });

  it('given an applicable code and an automatic coupon that takes more (UC-23), when the cart opens, then the code is not added', async () => {
    await openWith(cart([item()], 400_000, pijama10, { code: 'VERANO20', automatic: true }));

    expect(within(panel()).getByText(es.cart.coupon('PIJAMA10'))).toBeInTheDocument();
    expect(
      within(panel()).getByText('Tu cupón PIJAMA10 no se suma: ya tenés un descuento mejor.'),
    ).toBeInTheDocument();
    expect(within(panel()).getByText(es.cart.discountWith('VERANO20'))).toBeInTheDocument();
    expect(within(panel()).queryByText(es.cart.discountWith('PIJAMA10'))).not.toBeInTheDocument();
  });

  it('given an applied coupon, when "Quitar" is tapped, then it is removed', async () => {
    await openWith(cart([item()], 200_000, pijama10));
    const remove = vi.spyOn(api, 'DELETE').mockResolvedValue(cart([item()]));

    await userEvent.click(within(panel()).getByRole('button', { name: es.cart.removeCouponLabel('PIJAMA10') }));

    expect(remove).toHaveBeenCalledWith('/api/cart/coupon');
    await vi.waitFor(() => {
      expect(within(panel()).queryByText(es.cart.coupon('PIJAMA10'))).not.toBeInTheDocument();
    });
    expect(within(panel()).queryByText(es.cart.discountWith('PIJAMA10'))).not.toBeInTheDocument();
  });

  it('given a failed removal, when "Quitar" is tapped, then the panel asks to try again', async () => {
    await openWith(cart([item()], 200_000, pijama10));
    vi.spyOn(api, 'DELETE').mockResolvedValue(answer(500));

    await userEvent.click(within(panel()).getByRole('button', { name: es.cart.removeCouponLabel('PIJAMA10') }));

    expect(await within(panel()).findByText(es.cart.couponRemoveError)).toHaveAttribute('role', 'alert');
  });

  it('given a cart emptied in another tab, when a code is applied, then the cart is read again', async () => {
    const get = await openWith(cart([item()]), cart([]));
    vi.spyOn(api, 'POST').mockResolvedValue(answer(409, { problem: 'EMPTY_CART' }));

    await applyCode('pijama10');

    expect(await within(panel()).findByText(es.cart.empty)).toBeInTheDocument();
    expect(cartReads(get)).toHaveLength(2);
  });

  it('given too many attempts, when a code is applied, then it asks to wait a minute', async () => {
    await openWith(cart([item()]));
    vi.spyOn(api, 'POST').mockResolvedValue(answer(429));

    await applyCode('pijama10');

    expect(await within(panel()).findByText(es.cart.couponTooManyAttempts)).toHaveAttribute('role', 'alert');
  });

  it('given a failed request, when a code is applied, then the panel asks to try again', async () => {
    await openWith(cart([item()]));
    vi.spyOn(api, 'POST').mockResolvedValue(answer(500));

    await applyCode('pijama10');

    expect(await within(panel()).findByText(es.cart.couponApplyError)).toHaveAttribute('role', 'alert');
  });
});
