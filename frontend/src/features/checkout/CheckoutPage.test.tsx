import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { cart, cartItem } from '../../test/cartApi.ts';
import { answer, NO_CART } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { goToPayment } from '../orders/redirect.ts';
import { keptTrackingToken } from './trackingTokenStore.ts';

vi.mock('../orders/redirect.ts', () => ({ goToPayment: vi.fn() }));

const TOKEN = 'b'.repeat(43);
const PAYMENT_URL = 'https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=1';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

/** The step-1 summary of the pink M pijama (ARS 25,000) to a province, with this shipping cost. */
function quote(province: string, shippingCents: number) {
  return answer(200, {
    province,
    subtotalCents: 2_500_000,
    discountCents: 0,
    shippingCents,
    totalCents: 2_500_000 + shippingCents,
  });
}

const SHIPPING: Record<string, unknown> = {
  SANTA_FE: quote('SANTA_FE', 450_000),
  CORDOBA: quote('CORDOBA', 800_000),
  JUJUY: answer(422, { problem: 'NO_SHIPPING', province: 'JUJUY' }),
};

/** GET answers: this cart (the same for every read) and the shipping costs above. */
function stubGets(currentCart: unknown = cart([cartItem()])) {
  return vi.spyOn(api, 'GET').mockImplementation(((path: string, options?: { params?: { query?: { province?: string } } }) => {
    if (path === '/api/checkout/shipping-cost') {
      return Promise.resolve(SHIPPING[options?.params?.query?.province ?? '']);
    }
    return Promise.resolve(path === '/api/cart' ? currentCart : NO_CART);
  }) as never);
}

function stubPost(...answers: unknown[]) {
  const queue = [...answers];
  return vi.spyOn(api, 'POST').mockImplementation((() =>
    Promise.resolve(queue.length > 1 ? queue.shift() : queue[0])) as never);
}

const placed = (paymentUrl: string | null) =>
  answer(201, {
    number: 1001,
    trackingToken: TOKEN,
    status: 'AWAITING_PAYMENT',
    totalCents: 2_950_000,
    reservationExpiresAt: '2026-10-13T17:30:00Z',
    paymentUrl,
  });

const field = (name: string) => screen.getByLabelText(name);
const next = () => screen.getByRole('button', { name: es.checkout.toPayment });

async function fillShipping(overrides: Partial<Record<string, string>> = {}) {
  const values: Record<string, string> = {
    [es.checkout.fields.name]: 'Ana Pérez',
    [es.checkout.fields.email]: 'ana@example.com',
    [es.checkout.fields.phone]: '342 456 7890',
    [es.checkout.fields.nationalId]: '30123456',
    [es.checkout.fields.street]: 'San Martín',
    [es.checkout.fields.number]: '1234',
    [es.checkout.fields.city]: 'Santa Fe',
    [es.checkout.fields.postalCode]: '3000',
    ...overrides,
  };
  for (const [label, value] of Object.entries(values)) {
    if (value !== '') {
      await userEvent.type(field(label), value);
    }
  }
}

async function chooseProvince(province: string) {
  await userEvent.selectOptions(field(es.checkout.fields.province), province);
}

const summary = () => within(screen.getByRole('complementary', { name: es.checkout.summaryTitle }));
const totalOfSummary = () => summary().getByText(es.checkout.total).nextElementSibling;

/** Fills step 1 to Santa Fe, then goes through step 2 to the review. */
async function goToReview() {
  await screen.findByRole('heading', { name: es.checkout.shippingTitle });
  await fillShipping();
  await chooseProvince('SANTA_FE');
  await waitFor(() => {
    expect(totalOfSummary()).toHaveTextContent(/^\$\s29\.500$/);
  });
  await userEvent.click(next());
  await userEvent.click(await screen.findByRole('button', { name: es.checkout.toReview }));
  await screen.findByRole('heading', { name: es.checkout.reviewTitle });
}

const confirmButton = () => screen.getByRole('button', { name: es.checkout.confirm });

async function acceptAndConfirm() {
  await userEvent.click(screen.getByRole('checkbox'));
  await userEvent.click(confirmButton());
}

// Typing a whole form takes a few seconds, more in a busy parallel run.
describe('checkout in 3 steps (UC-13, T-E3-11)', { timeout: 30_000 }, () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.mocked(goToPayment).mockClear();
  });

  it('given an empty required field, when trying to go on, then it does not go on and the missing field is shown', async () => {
    stubGets();
    renderRoute('/checkout');
    await screen.findByRole('heading', { name: es.checkout.shippingTitle });

    await fillShipping({ [es.checkout.fields.street]: '' });
    await chooseProvince('SANTA_FE');
    await userEvent.click(next());

    const street = field(es.checkout.fields.street);
    expect(street).toHaveAttribute('aria-invalid', 'true');
    expect(street).toHaveAccessibleDescription(es.checkout.errors.street);
    expect(street).toHaveFocus();
    expect(screen.queryByRole('heading', { name: es.checkout.paymentTitle })).not.toBeInTheDocument();
  });

  it('given a national ID with an invalid format, when validated, then it is rejected', async () => {
    stubGets();
    renderRoute('/checkout');
    await screen.findByRole('heading', { name: es.checkout.shippingTitle });

    await fillShipping({ [es.checkout.fields.nationalId]: '12345' });
    await chooseProvince('SANTA_FE');
    await userEvent.click(next());

    expect(field(es.checkout.fields.nationalId)).toHaveAccessibleDescription(es.checkout.errors.nationalId);
    expect(screen.queryByRole('heading', { name: es.checkout.paymentTitle })).not.toBeInTheDocument();
  });

  it('given a cart with a variant out of stock, when the checkout opens, then it is announced and the cart must be fixed to go on', async () => {
    stubGets(cart([cartItem(), cartItem({ id: 'item-2', variantId: 'L-Rosa', status: 'UNAVAILABLE', available: 0 })]));
    renderRoute('/checkout');

    expect(await screen.findByText(es.checkout.cartNotReady)).toBeInTheDocument();
    expect(next()).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: es.checkout.reviewCart }));

    expect(screen.getByRole('dialog', { name: es.cart.title })).toHaveAttribute('open');
  });

  it('given a province without a cost, when the buyer picks it, then it is told there is no shipping to that zone', async () => {
    stubGets();
    renderRoute('/checkout');
    await screen.findByRole('heading', { name: es.checkout.shippingTitle });

    await fillShipping();
    await chooseProvince('JUJUY');

    expect(await screen.findByText(es.checkout.noShipping)).toBeInTheDocument();
    await userEvent.click(next());
    expect(screen.queryByRole('heading', { name: es.checkout.paymentTitle })).not.toBeInTheDocument();
  });

  it('given a buyer who goes back a step, when the province changes, then the shipping cost and the total are recalculated', async () => {
    const get = stubGets();
    renderRoute('/checkout');
    await screen.findByRole('heading', { name: es.checkout.shippingTitle });
    expect(summary().getByText(es.checkout.shippingPending)).toBeInTheDocument();

    await fillShipping();
    await chooseProvince('SANTA_FE');
    await waitFor(() => {
      expect(totalOfSummary()).toHaveTextContent(/^\$\s29\.500$/);
    });
    await userEvent.click(next());
    await userEvent.click(await screen.findByRole('button', { name: es.checkout.back }));
    await chooseProvince('CORDOBA');

    await waitFor(() => {
      expect(totalOfSummary()).toHaveTextContent(/^\$\s33\.000$/);
    });
    expect(get).toHaveBeenCalledWith('/api/checkout/shipping-cost', expect.objectContaining({
      params: { query: { province: 'CORDOBA' } },
    }));
  });

  it('given a confirmed checkout, when the order is placed, then the buyer goes to the provider and the token is kept', async () => {
    stubGets();
    const post = stubPost(placed(PAYMENT_URL));
    renderRoute('/checkout');
    await goToReview();

    await acceptAndConfirm();

    await waitFor(() => {
      expect(goToPayment).toHaveBeenCalledWith(PAYMENT_URL);
    });
    expect(post).toHaveBeenCalledWith('/api/checkout', {
      params: { header: { 'Idempotency-Key': expect.stringMatching(UUID) as unknown } },
      body: {
        buyer: { name: 'Ana Pérez', email: 'ana@example.com', phone: '342 456 7890', nationalId: '30123456' },
        address: { street: 'San Martín', number: '1234', city: 'Santa Fe', province: 'SANTA_FE', postalCode: '3000' },
        paymentProvider: 'MERCADOPAGO',
        acceptedTerms: true,
      },
    });
    expect(keptTrackingToken()).toBe(TOKEN);
    expect(screen.getByText(es.checkout.openingPayment)).toBeInTheDocument();
  });

  it('given a provider that could not be reached, when the order is placed, then the tracking page opens', async () => {
    stubGets();
    stubPost(placed(null));
    const { router } = renderRoute('/checkout');
    await goToReview();

    await acceptAndConfirm();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(`/pedido/${TOKEN}`);
    });
    expect(goToPayment).not.toHaveBeenCalled();
  });

  it('given the terms not accepted, when confirming, then nothing is sent and the checkbox says why', async () => {
    stubGets();
    const post = stubPost(placed(PAYMENT_URL));
    renderRoute('/checkout');
    await goToReview();

    await userEvent.click(confirmButton());

    expect(screen.getByRole('checkbox')).toHaveAccessibleDescription(es.checkout.termsRequired);
    expect(post).not.toHaveBeenCalled();
  });

  it('given buyer data the server refuses, when confirming, then step 1 shows the named fields', async () => {
    stubGets();
    stubPost(answer(422, { problem: 'INVALID_BUYER_DATA', fields: ['postalCode'] }));
    renderRoute('/checkout');
    await goToReview();

    await acceptAndConfirm();

    const postalCode = await screen.findByLabelText(es.checkout.fields.postalCode);
    expect(postalCode).toHaveAccessibleDescription(es.checkout.errors.postalCode);
    expect(screen.getByRole('heading', { name: es.checkout.shippingTitle })).toBeInTheDocument();
  });

  it('given a coupon that no longer applies, when confirming, then the buyer is told to remove it', async () => {
    stubGets();
    stubPost(answer(409, { problem: 'COUPON_NOT_APPLICABLE', couponStatus: 'EXPIRED' }));
    renderRoute('/checkout');
    await goToReview();

    await acceptAndConfirm();

    expect(await screen.findByText(es.checkout.refused.couponNotApplicable)).toBeInTheDocument();
  });

  it('given a confirmation without an answer, when retried, then the same Idempotency-Key is repeated', async () => {
    stubGets();
    const post = stubPost(answer(503), answer(409, { problem: 'CART_NOT_READY' }), placed(PAYMENT_URL));
    renderRoute('/checkout');
    await goToReview();

    await acceptAndConfirm();
    expect(await screen.findByText(es.checkout.refused.unknown)).toBeInTheDocument();
    await userEvent.click(confirmButton());
    expect(await screen.findByText(es.checkout.refused.cartNotReady)).toBeInTheDocument();
    await userEvent.click(confirmButton());

    await waitFor(() => {
      expect(post).toHaveBeenCalledTimes(3);
    });
    const keys = post.mock.calls.map(
      (call) => (call[1] as { params: { header: Record<string, string> } }).params.header['Idempotency-Key'],
    );
    expect(keys[1]).toBe(keys[0]);
    expect(keys[2]).not.toBe(keys[1]);
  });

  it('given an empty cart, when the checkout opens, then it says so with a way back to the store', async () => {
    stubGets(NO_CART);
    renderRoute('/checkout');

    // The closed cart panel says the same, so only the page is searched.
    const page = within(screen.getByRole('main'));
    expect(await page.findByText(es.checkout.empty)).toBeInTheDocument();
    expect(page.getByRole('link', { name: es.checkout.goShopping })).toHaveAttribute('href', '/');
  });

  it('given a cart with items, when the cart panel starts the purchase, then the checkout opens', async () => {
    stubGets();
    const { router } = renderRoute('/buscar');

    await userEvent.click(await screen.findByRole('button', { name: es.cart.open(1) }));
    await userEvent.click(await screen.findByRole('button', { name: es.cart.startCheckout }));

    expect(router.state.location.pathname).toBe('/checkout');
  });

  it('given a cart without available items, when the cart panel opens, then the purchase cannot start', async () => {
    stubGets(cart([cartItem({ status: 'UNAVAILABLE', available: 0 })]));
    renderRoute('/buscar');

    await userEvent.click(await screen.findByRole('button', { name: es.cart.open(1) }));

    expect(await screen.findByRole('button', { name: es.cart.startCheckout })).toBeDisabled();
  });
});
