import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { cart, cartItem } from '../../test/cartApi.ts';
import { answer, detailOrNoReviews, NO_CART, SHIPPING_RATES_PATH, withHeader } from '../../test/catalogApi.ts';
import { forgetMotionPreference, preferReducedMotion } from '../../test/reducedMotion.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

// Loaded up front, so the app's MotionProvider is ready before the first tap, as after the first paint.
await import('../../components/motion/motionFeatures.ts');

/** The product page of the pink M pijama (3 in stock), with this cart in the header. */
function stubStore(currentCart: unknown = NO_CART) {
  vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) =>
    Promise.resolve(path === '/api/cart' ? currentCart : detailOrNoReviews(path))));
}

async function addPinkM() {
  await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));
  await userEvent.click(screen.getByRole('button', { name: es.product.addToCart }));
}

const badge = (units: number) =>
  screen.getByRole('button', { name: es.cart.open(units), hidden: true }).querySelector<HTMLElement>('.cart-badge');

const NOT_MOVED = /^(none)?$/;

describe('add to cart motion (E7, D-16)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    forgetMotionPreference();
  });

  it('given a product added from the product page, when the API answers 200, then the cart icon bumps once and the button says "Agregado"', async () => {
    preferReducedMotion(false);
    stubStore();
    vi.spyOn(api, 'POST').mockResolvedValue(cart([cartItem()]));
    renderRoute('/producto/luna', { motion: true });

    await addPinkM();

    // Read while moving: the panel is still sliding in and the counter still bumping.
    expect((await screen.findByRole('dialog', { name: es.cart.title })).style.transform).toMatch(/^translateX\(/);
    expect(badge(1)?.style.transform).toMatch(/^scale\(/);
    expect(screen.getByRole('button', { name: es.product.added, hidden: true })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: es.product.addToCart, hidden: true })).toBeInTheDocument();
    });
  });

  it('given a rejected add, when the API answers 409, then the cart icon does not bump and the button does not say "Agregado"', async () => {
    preferReducedMotion(false);
    stubStore(cart([cartItem({ quantity: 2 })]));
    vi.spyOn(api, 'POST').mockResolvedValue(
      answer(409, { problem: 'INSUFFICIENT_STOCK', variantId: 'M-Rosa', available: 3 }),
    );
    renderRoute('/producto/luna', { motion: true });
    expect(await screen.findByRole('button', { name: es.cart.open(2) })).toBeInTheDocument();

    await addPinkM();

    expect(await screen.findByText(es.product.onlyLeft(3))).toBeInTheDocument();
    expect(badge(2)?.style.transform ?? '').toMatch(NOT_MOVED);
    expect(screen.queryByRole('button', { name: es.product.added })).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('given reduced motion, when the shopper adds a product and the cart opens, then nothing translates or scales', async () => {
    preferReducedMotion(true);
    stubStore();
    vi.spyOn(api, 'POST').mockResolvedValue(cart([cartItem()]));
    renderRoute('/producto/luna', { motion: true });

    await addPinkM();

    const panel = await screen.findByRole('dialog', { name: es.cart.title });
    expect(panel.style.transform).toMatch(NOT_MOVED);
    expect(badge(1)?.style.transform).toMatch(NOT_MOVED);
    const check = screen.getByRole('button', { name: es.product.added, hidden: true }).querySelector('svg');
    expect(check?.style.transform).toMatch(NOT_MOVED);
  });
});

describe('free-shipping bar motion (E7, D-16)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    forgetMotionPreference();
  });

  /** A $50,000 threshold and this cart, with the panel opened from the header. */
  async function openWithThreshold(currentCart: unknown) {
    vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) => {
      if (path === SHIPPING_RATES_PATH) {
        return Promise.resolve(answer(200, { zones: [], freeShippingThresholdCents: 5_000_000 }));
      }
      return Promise.resolve(path === '/api/cart' ? currentCart : detailOrNoReviews(path));
    }));
    renderRoute('/producto/luna', { motion: true });
    await userEvent.click(await screen.findByRole('button', { name: /^Carrito/ }));
    return screen.findByRole('dialog', { name: es.cart.title });
  }

  it('given a cart below the threshold, when "+" reaches it, then "¡Tenés envío gratis!" pops in', async () => {
    preferReducedMotion(false);
    const panel = await openWithThreshold(cart([cartItem()]));
    vi.spyOn(api, 'PATCH').mockResolvedValue(cart([cartItem({ quantity: 2 })]));

    await userEvent.click(await within(panel).findByRole('button', { name: es.cart.increase('Pijama Luna') }));

    expect((await within(panel).findByText(es.cart.freeShippingReached)).style.transform).toMatch(/scale\(/);
  });

  it('given a cart already over the threshold, when the panel opens, then the message shows in place', async () => {
    preferReducedMotion(false);
    const panel = await openWithThreshold(cart([cartItem({ quantity: 2 })]));

    expect((await within(panel).findByText(es.cart.freeShippingReached)).style.transform).toMatch(NOT_MOVED);
  });

  it('given reduced motion, when "+" reaches the threshold, then the message does not scale', async () => {
    preferReducedMotion(true);
    const panel = await openWithThreshold(cart([cartItem()]));
    vi.spyOn(api, 'PATCH').mockResolvedValue(cart([cartItem({ quantity: 2 })]));

    await userEvent.click(await within(panel).findByRole('button', { name: es.cart.increase('Pijama Luna') }));

    expect((await within(panel).findByText(es.cart.freeShippingReached)).style.transform).toMatch(NOT_MOVED);
  });
});
