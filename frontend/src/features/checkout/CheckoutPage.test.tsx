import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { cart, cartItem } from '../../test/cartApi.ts';
import { answer, NO_CART, withHeader } from '../../test/catalogApi.ts';
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

const MODO_OFF = answer(200, { providers: ['MERCADOPAGO'] });
const MODO_ON = answer(200, { providers: ['MERCADOPAGO', 'MODO'] });

/** GET answers: this cart (the same for every read), the shipping costs above and these payment providers. */
function stubGets(currentCart: unknown = cart([cartItem()]), providers: unknown = MODO_OFF) {
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string, options?: { params?: { query?: { province?: string } } }) => {
    if (path === '/api/checkout/shipping-cost') {
      return Promise.resolve(SHIPPING[options?.params?.query?.province ?? '']);
    }
    if (path === '/api/checkout/payment-providers') {
      return Promise.resolve(providers);
    }
    return Promise.resolve(path === '/api/cart' ? currentCart : NO_CART);
  }));
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

const escaped = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
/** "¿Quisiste decir {email}?", with or without a space before the "?" that follows the button. */
const suggesting = (email: string) => new RegExp(`^${escaped(es.checkout.emailSuggestion)} ${escaped(email)} ?\\?$`);

async function fillShipping(overrides: Partial<Record<string, string>> = {}) {
  const values: Record<string, string> = {
    [es.checkout.fields.firstName]: 'Ana',
    [es.checkout.fields.lastName]: 'Pérez',
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

/** Fills step 1 to Santa Fe and goes on to step 2. */
async function goToPaymentStep() {
  await screen.findByRole('heading', { name: es.checkout.shippingTitle });
  await fillShipping();
  await chooseProvince('SANTA_FE');
  await waitFor(() => {
    expect(totalOfSummary()).toHaveTextContent(/^\$\s29\.500$/);
  });
  await userEvent.click(next());
  await screen.findByRole('heading', { name: es.checkout.paymentTitle });
}

/** Fills step 1 to Santa Fe, then goes through step 2 to the review, choosing this provider. */
async function goToReview(providerName?: string) {
  await goToPaymentStep();
  if (providerName !== undefined) {
    await userEvent.click(await screen.findByRole('radio', { name: new RegExp(`^${escaped(providerName)}`) }));
  }
  await userEvent.click(screen.getByRole('button', { name: es.checkout.toReview }));
  await screen.findByRole('heading', { name: es.checkout.reviewTitle });
}

const providerOptions = () => screen.getAllByRole('radio').map((radio) => (radio as HTMLInputElement).value);

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

  it('given an empty first name and a blank last name (D-13), when trying to go on, then each is marked', async () => {
    stubGets();
    renderRoute('/checkout');
    await screen.findByRole('heading', { name: es.checkout.shippingTitle });

    await fillShipping({ [es.checkout.fields.firstName]: '', [es.checkout.fields.lastName]: ' ' });
    await chooseProvince('SANTA_FE');
    await userEvent.click(next());

    const firstName = field(es.checkout.fields.firstName);
    expect(firstName).toHaveAccessibleDescription(es.checkout.errors.firstName);
    expect(firstName).toHaveFocus();
    expect(field(es.checkout.fields.lastName)).toHaveAccessibleDescription(es.checkout.errors.lastName);
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

  it('given an email with a misspelled domain, when the field loses the focus, then a correction is suggested and can be applied', async () => {
    stubGets();
    renderRoute('/checkout');
    await screen.findByRole('heading', { name: es.checkout.shippingTitle });

    await userEvent.type(field(es.checkout.fields.email), 'ana@gmial.com');
    await userEvent.tab();

    const email = field(es.checkout.fields.email);
    expect(email).toHaveAccessibleDescription(suggesting('ana@gmail.com'));
    await userEvent.click(screen.getByRole('button', { name: 'ana@gmail.com' }));

    expect(email).toHaveValue('ana@gmail.com');
    expect(email).toHaveFocus();
    expect(screen.queryByRole('button', { name: 'ana@gmail.com' })).not.toBeInTheDocument();
  });

  it('given an email with a misspelled domain, when the buyer goes on, then it stops once at the suggestion without blocking', async () => {
    stubGets();
    renderRoute('/checkout');
    await screen.findByRole('heading', { name: es.checkout.shippingTitle });

    await fillShipping({ [es.checkout.fields.email]: 'ana@hotmal.com' });
    await chooseProvince('SANTA_FE');
    await waitFor(() => {
      expect(totalOfSummary()).toHaveTextContent(/^\$\s29\.500$/);
    });
    await userEvent.click(next());

    const email = field(es.checkout.fields.email);
    expect(email).toHaveFocus();
    expect(email).toHaveAccessibleDescription(suggesting('ana@hotmail.com'));
    expect(email).toHaveAttribute('aria-invalid', 'false');
    expect(screen.queryByRole('heading', { name: es.checkout.paymentTitle })).not.toBeInTheDocument();

    await userEvent.click(next());

    expect(await screen.findByRole('heading', { name: es.checkout.paymentTitle })).toBeInTheDocument();
  });

  it('given an email with a common domain, when the buyer goes on, then nothing is suggested', async () => {
    stubGets();
    renderRoute('/checkout');
    await screen.findByRole('heading', { name: es.checkout.shippingTitle });

    await fillShipping({ [es.checkout.fields.email]: 'ana@gmail.com' });
    await chooseProvince('SANTA_FE');
    await waitFor(() => {
      expect(totalOfSummary()).toHaveTextContent(/^\$\s29\.500$/);
    });
    await userEvent.click(next());

    expect(await screen.findByRole('heading', { name: es.checkout.paymentTitle })).toBeInTheDocument();
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
        buyer: {
          firstName: 'Ana',
          lastName: 'Pérez',
          email: 'ana@example.com',
          phone: '342 456 7890',
          nationalId: '30123456',
        },
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

  it('given MODO switched off, when the payment step opens, then only MercadoPago is offered, chosen', async () => {
    stubGets();
    renderRoute('/checkout');

    await goToPaymentStep();

    expect(providerOptions()).toEqual(['MERCADOPAGO']);
    expect(screen.getByRole('radio', { name: /^MercadoPago/ })).toBeChecked();
    expect(screen.getByText(es.checkout.providers.MERCADOPAGO.hint)).toBeInTheDocument();
  });

  it('given the payment providers could not be read, when the payment step opens, then MercadoPago is offered', async () => {
    stubGets(cart([cartItem()]), answer(500));
    renderRoute('/checkout');

    await goToPaymentStep();

    expect(providerOptions()).toEqual(['MERCADOPAGO']);
  });

  it('given MODO switched on, when the buyer chooses it and confirms, then the order is placed with MODO', async () => {
    stubGets(cart([cartItem()]), MODO_ON);
    const post = stubPost(placed('https://site.modo.com.ar/checkout?id=pr-1'));
    renderRoute('/checkout');

    await goToPaymentStep();
    await waitFor(() => {
      expect(providerOptions()).toEqual(['MERCADOPAGO', 'MODO']);
    });
    expect(screen.getByRole('radio', { name: /^MercadoPago/ })).toBeChecked();
    expect(screen.getByText(es.checkout.providers.MODO.hint)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('radio', { name: /^MODO/ }));
    await userEvent.click(screen.getByRole('button', { name: es.checkout.toReview }));
    await screen.findByRole('heading', { name: es.checkout.reviewTitle });
    expect(within(screen.getByRole('main')).getByText(es.checkout.providers.MODO.name)).toBeInTheDocument();

    await acceptAndConfirm();

    await waitFor(() => {
      expect(goToPayment).toHaveBeenCalledWith('https://site.modo.com.ar/checkout?id=pr-1');
    });
    expect(post).toHaveBeenCalledWith('/api/checkout', expect.objectContaining({
      body: expect.objectContaining({ paymentProvider: 'MODO' }) as unknown,
    }));
  });

  it('given MODO switched off after the page read it, when confirming with MODO, then the payment step asks for another method', async () => {
    const get = stubGets(cart([cartItem()]), MODO_ON);
    stubPost(answer(400, { problem: 'PAYMENT_PROVIDER_NOT_ENABLED', paymentProvider: 'MODO' }));
    renderRoute('/checkout');
    await goToReview(es.checkout.providers.MODO.name);
    get.mockImplementation(((path: string) =>
      Promise.resolve(path === '/api/checkout/payment-providers' ? MODO_OFF : cart([cartItem()]))) as never);

    await acceptAndConfirm();

    expect(await screen.findByRole('heading', { name: es.checkout.paymentTitle })).toHaveFocus();
    expect(screen.getByText(es.checkout.refused.paymentProviderNotEnabled)).toBeInTheDocument();
    await waitFor(() => {
      expect(providerOptions()).toEqual(['MERCADOPAGO']);
    });
    expect(screen.getByRole('radio', { name: /^MercadoPago/ })).toBeChecked();
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

  it('given an applicable code and an automatic coupon that takes more (UC-23), when the checkout opens, then the summary says the code is not added', async () => {
    stubGets(
      cart([cartItem()], 500_000, { code: 'PIJAMA10', status: 'APPLICABLE', missingCents: 0 }, {
        code: 'VERANO20',
        automatic: true,
      }),
    );
    renderRoute('/checkout');

    await screen.findByRole('heading', { name: es.checkout.shippingTitle });

    expect(summary().getByText(es.cart.coupon('PIJAMA10'))).toBeInTheDocument();
    expect(summary().getByText('Tu cupón PIJAMA10 no se suma: ya tenés un descuento mejor.')).toBeInTheDocument();
    expect(summary().getByText(es.checkout.discountWith('VERANO20')).nextElementSibling).toHaveTextContent(
      /^−\$\s5\.000$/,
    );
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

  it('given the same key still being processed, when retried, then the same Idempotency-Key is repeated', async () => {
    stubGets();
    const post = stubPost(answer(409, { problem: 'IDEMPOTENCY_KEY_IN_USE' }), placed(PAYMENT_URL));
    renderRoute('/checkout');
    await goToReview();

    await acceptAndConfirm();
    expect(await screen.findByText(es.checkout.refused.unknown)).toBeInTheDocument();
    await userEvent.click(confirmButton());

    await waitFor(() => {
      expect(post).toHaveBeenCalledTimes(2);
    });
    const keys = post.mock.calls.map(
      (call) => (call[1] as { params: { header: Record<string, string> } }).params.header['Idempotency-Key'],
    );
    expect(keys[1]).toBe(keys[0]);
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

const ANA = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'ana@example.com',
  name: 'Ana María Gómez',
  phone: '3424567890',
};

function savedAddress(id: number, street: string, primary: boolean) {
  return {
    id: `00000000-0000-0000-0000-0000000001${String(id).padStart(2, '0')}`,
    street,
    number: '1234',
    floor: '3',
    apartment: null,
    city: 'Santa Fe',
    province: 'SANTA_FE',
    postalCode: 'S3000ABC',
    primary,
  };
}

/** GET answers for a signed-in customer with this address book; the rest as stubGets. */
function stubCustomerGets(addresses: unknown[], customer: unknown = ANA) {
  return vi.spyOn(api, 'GET').mockImplementation(
    withHeader(
      (path: string, options?: { params?: { query?: { province?: string } } }) => {
        if (path === '/api/account/addresses') {
          return Promise.resolve(answer(200, addresses));
        }
        if (path === '/api/checkout/shipping-cost') {
          return Promise.resolve(SHIPPING[options?.params?.query?.province ?? '']);
        }
        if (path === '/api/checkout/payment-providers') {
          return Promise.resolve(MODO_OFF);
        }
        return Promise.resolve(path === '/api/cart' ? cart([cartItem()]) : NO_CART);
      },
      answer(200, customer),
    ),
  );
}

const addressChoice = (name: string | RegExp) => screen.getByRole('radio', { name });

/** Goes from step 1 as it is to the confirmation, accepting the terms. */
async function confirmFromShipping() {
  await waitFor(() => {
    expect(totalOfSummary()).toHaveTextContent(/^\$\s29\.500$/);
  });
  await userEvent.click(next());
  await userEvent.click(await screen.findByRole('button', { name: es.checkout.toReview }));
  await screen.findByRole('heading', { name: es.checkout.reviewTitle });
  await acceptAndConfirm();
}

describe('checkout with prefilled data (UC-21, T-E4-06)', { timeout: 30_000 }, () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a customer with a primary address, when it opens the checkout, then that address is selected and its data prefilled', async () => {
    stubCustomerGets([savedAddress(1, 'Rivadavia', false), savedAddress(2, 'San Martín', true)]);
    renderRoute('/checkout');

    await waitFor(() => {
      expect(addressChoice(/^San Martín 1234, piso 3/)).toBeChecked();
    });
    expect(addressChoice(/^Rivadavia 1234/)).not.toBeChecked();
    expect(addressChoice(es.checkout.otherAddress)).not.toBeChecked();
    expect(field(es.checkout.fields.firstName)).toHaveValue('Ana');
    expect(field(es.checkout.fields.lastName)).toHaveValue('María Gómez');
    expect(field(es.checkout.fields.email)).toHaveValue('ana@example.com');
    expect(field(es.checkout.fields.phone)).toHaveValue('3424567890');
    expect(field(es.checkout.fields.nationalId)).toHaveValue('');
    expect(screen.queryByLabelText(es.checkout.fields.street)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(es.checkout.saveAddress)).not.toBeInTheDocument();
  });

  it('given a saved address chosen, when the customer confirms, then the order carries that address and saves nothing', async () => {
    stubCustomerGets([savedAddress(1, 'San Martín', true)]);
    const post = stubPost(placed(PAYMENT_URL));
    renderRoute('/checkout');
    await waitFor(() => {
      expect(addressChoice(/^San Martín 1234/)).toBeChecked();
    });
    await userEvent.type(field(es.checkout.fields.nationalId), '30123456');

    await confirmFromShipping();

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith('/api/checkout', {
        params: { header: { 'Idempotency-Key': expect.stringMatching(UUID) as unknown } },
        body: {
          buyer: {
            firstName: 'Ana',
            lastName: 'María Gómez',
            email: 'ana@example.com',
            phone: '3424567890',
            nationalId: '30123456',
          },
          address: {
            street: 'San Martín',
            number: '1234',
            floor: '3',
            city: 'Santa Fe',
            province: 'SANTA_FE',
            postalCode: 'S3000ABC',
          },
          paymentProvider: 'MERCADOPAGO',
          acceptedTerms: true,
        },
      });
    });
  });

  it('given another address, when the customer types one and asks to save it, then the order saves it to the book', async () => {
    stubCustomerGets([savedAddress(1, 'San Martín', true)]);
    const post = stubPost(placed(PAYMENT_URL));
    renderRoute('/checkout');
    await waitFor(() => {
      expect(addressChoice(/^San Martín 1234/)).toBeChecked();
    });

    await userEvent.click(addressChoice(es.checkout.otherAddress));

    expect(field(es.checkout.fields.street)).toHaveValue('');
    expect(field(es.checkout.fields.province)).toHaveValue('');
    expect(screen.getByLabelText(es.checkout.saveAddress)).not.toBeChecked();
    await userEvent.type(field(es.checkout.fields.nationalId), '30123456');
    await userEvent.type(field(es.checkout.fields.street), 'Rivadavia');
    await userEvent.type(field(es.checkout.fields.number), '550');
    await userEvent.type(field(es.checkout.fields.city), 'Rosario');
    await userEvent.type(field(es.checkout.fields.postalCode), '2000');
    await chooseProvince('SANTA_FE');
    await userEvent.click(screen.getByLabelText(es.checkout.saveAddress));
    await confirmFromShipping();

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith('/api/checkout', {
        params: { header: { 'Idempotency-Key': expect.stringMatching(UUID) as unknown } },
        body: {
          buyer: {
            firstName: 'Ana',
            lastName: 'María Gómez',
            email: 'ana@example.com',
            phone: '3424567890',
            nationalId: '30123456',
          },
          address: { street: 'Rivadavia', number: '550', city: 'Rosario', province: 'SANTA_FE', postalCode: '2000' },
          paymentProvider: 'MERCADOPAGO',
          acceptedTerms: true,
          saveAddress: true,
        },
      });
    });
  });

  it('given a customer without addresses or a phone, when it opens the checkout, then it types the address and may save it', async () => {
    stubCustomerGets([], { ...ANA, phone: null });
    renderRoute('/checkout');

    expect(await screen.findByLabelText(es.checkout.saveAddress)).not.toBeChecked();
    expect(field(es.checkout.fields.firstName)).toHaveValue('Ana');
    expect(field(es.checkout.fields.lastName)).toHaveValue('María Gómez');
    expect(field(es.checkout.fields.phone)).toHaveValue('');
    expect(screen.queryByRole('group', { name: es.checkout.savedAddresses })).not.toBeInTheDocument();
    expect(field(es.checkout.fields.street)).toHaveValue('');
  });

  it('given a full address book, when the customer types another address, then it is not offered to save it', async () => {
    stubCustomerGets(Array.from({ length: 10 }, (_, i) => savedAddress(i, `Calle ${String(i)}`, i === 0)));
    renderRoute('/checkout');
    await waitFor(() => {
      expect(addressChoice(/^Calle 0 1234/)).toBeChecked();
    });

    await userEvent.click(addressChoice(es.checkout.otherAddress));

    expect(field(es.checkout.fields.street)).toBeInTheDocument();
    expect(screen.queryByLabelText(es.checkout.saveAddress)).not.toBeInTheDocument();
  });

  it('given a guest, when it opens the checkout, then nothing is prefilled and no address is offered', async () => {
    stubGets();
    renderRoute('/checkout');

    await screen.findByRole('heading', { name: es.checkout.shippingTitle });

    expect(field(es.checkout.fields.firstName)).toHaveValue('');
    expect(field(es.checkout.fields.lastName)).toHaveValue('');
    expect(screen.queryByRole('group', { name: es.checkout.savedAddresses })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(es.checkout.saveAddress)).not.toBeInTheDocument();
  });

  it('given a guest on step 1 (T-E7-10), when it looks for its account, then it can sign in and come back', async () => {
    stubGets();
    renderRoute('/checkout');

    const signIn = await screen.findByRole('link', { name: es.checkout.signIn });

    expect(signIn).toHaveAttribute('href', '/cuenta/ingresar?volver=%2Fcheckout');
  });

  it('given a signed-in customer, when it opens the checkout, then it is not offered to sign in', async () => {
    stubCustomerGets([]);
    renderRoute('/checkout');

    expect(await screen.findByLabelText(es.checkout.saveAddress)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: es.checkout.signIn })).not.toBeInTheDocument();
  });
});
