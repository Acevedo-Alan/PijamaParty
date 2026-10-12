import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { cart, cartItem } from '../../test/cartApi.ts';
import { answer, NO_CART, productDetail } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { resetAnalyticsForTests } from './analytics.ts';
import { saveConsent } from './consent.ts';

function gaEvents(name: string): unknown[] {
  return (window.dataLayer ?? [])
    .map((entry) => [...(entry as IArguments)] as unknown[])
    .filter((command) => command[0] === 'event' && command[1] === name)
    .map((command) => command[2]);
}

function pixelEvents(name: string): unknown[] {
  return (window.fbq?.queue ?? []).filter((call) => call[0] === 'track' && call[1] === name).map((call) => call[2]);
}

/** The product page of the pink M pijama (3 in stock, ARS 25,000), with an empty cart in the header. */
function stubStore() {
  vi.spyOn(api, 'GET').mockImplementation(((path: string) =>
    Promise.resolve(path === '/api/cart' ? NO_CART : answer(200, productDetail()))) as never);
}

async function addPinkM() {
  await userEvent.click(await screen.findByRole('radio', { name: 'Rosa' }));
  await userEvent.click(screen.getByRole('button', { name: es.product.addToCart }));
}

describe('cart analytics events (RNF-25, T-E2-12)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_GA4_MEASUREMENT_ID', 'G-ABC123');
    vi.stubEnv('VITE_META_PIXEL_ID', '1234567890');
    saveConsent('accepted');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
    resetAnalyticsForTests();
    localStorage.clear();
    document.head.querySelectorAll('script').forEach((script) => {
      script.remove();
    });
  });

  it('given a successful add, when it finishes, then add_to_cart and AddToCart go once with the SKU and the server price', async () => {
    stubStore();
    vi.spyOn(api, 'POST').mockResolvedValue(cart([cartItem({ unitPriceCents: 2_400_050 })]));
    renderRoute('/producto/luna');

    await addPinkM();
    await screen.findByRole('dialog', { name: es.cart.title });

    expect(gaEvents('add_to_cart')).toEqual([
      {
        currency: 'ARS',
        value: 24000.5,
        items: [{ item_id: 'PP-MROSA', item_name: 'Pijama Luna', item_variant: 'Rosa / M', price: 24000.5, quantity: 1 }],
      },
    ]);
    expect(pixelEvents('AddToCart')).toEqual([
      {
        content_ids: ['PP-MROSA'],
        content_type: 'product',
        contents: [{ id: 'PP-MROSA', quantity: 1 }],
        value: 24000.5,
        currency: 'ARS',
      },
    ]);
  });

  it('given a rejected add, when it finishes, then nothing is sent', async () => {
    stubStore();
    vi.spyOn(api, 'POST').mockResolvedValue(answer(409, { problem: 'INSUFFICIENT_STOCK', variantId: 'M-Rosa', available: 3 }));
    renderRoute('/producto/luna');

    await addPinkM();
    await screen.findByText(es.product.onlyLeft(3));

    expect(gaEvents('add_to_cart')).toEqual([]);
    expect(pixelEvents('AddToCart')).toEqual([]);
  });

  it('given an item in the cart, when "+" raises its quantity in the panel, then it is not an add and nothing is sent', async () => {
    vi.spyOn(api, 'GET').mockImplementation(((path: string) =>
      Promise.resolve(path === '/api/cart' ? cart([cartItem()]) : answer(200, productDetail()))) as never);
    const patch = vi.spyOn(api, 'PATCH').mockResolvedValue(cart([cartItem({ quantity: 2 })]));
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('button', { name: es.cart.open(1) }));
    await userEvent.click(await screen.findByRole('button', { name: es.cart.increase('Pijama Luna') }));

    await vi.waitFor(() => {
      expect(patch).toHaveBeenCalled();
    });
    expect(await screen.findByRole('button', { name: es.cart.open(2), hidden: true })).toBeInTheDocument();
    expect(gaEvents('add_to_cart')).toEqual([]);
    expect(pixelEvents('AddToCart')).toEqual([]);
  });

  it('given no consent, when a product is added, then nothing is sent', async () => {
    localStorage.clear();
    stubStore();
    vi.spyOn(api, 'POST').mockResolvedValue(cart([cartItem()]));
    renderRoute('/producto/luna');

    await addPinkM();
    await screen.findByRole('dialog', { name: es.cart.title });

    expect(window.dataLayer).toBeUndefined();
    expect(window.fbq).toBeUndefined();
  });
});
