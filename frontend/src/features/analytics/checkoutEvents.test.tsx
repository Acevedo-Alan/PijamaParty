import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { es } from '../../i18n/es.ts';
import { cart, cartItem } from '../../test/cartApi.ts';
import { answer, NO_CART } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { keepTrackingToken } from '../checkout/trackingTokenStore.ts';
import { CONFIRMING_EVERY_MS } from '../orders/useTrackedOrder.ts';
import { loadAnalytics, resetAnalyticsForTests } from './analytics.ts';
import { trackPurchase } from './checkoutEvents.ts';
import { saveConsent } from './consent.ts';

type TrackedOrderResponse = components['schemas']['TrackedOrderResponse'];
type OrderStatus = NonNullable<TrackedOrderResponse['status']>;

const TOKEN = 'd'.repeat(43);

function gaEvents(name: string): unknown[] {
  return (window.dataLayer ?? [])
    .map((entry) => [...(entry as IArguments)] as unknown[])
    .filter((command) => command[0] === 'event' && command[1] === name)
    .map((command) => command[2]);
}

/** The Pixel calls of an event, with their parameters and, when there are, their options. */
function pixelCalls(name: string): unknown[][] {
  return (window.fbq?.queue ?? []).filter((call) => call[0] === 'track' && call[1] === name).map((call) => call.slice(2));
}

/** A pink M pijama ready to buy, a blue L one no longer sold, and an applicable coupon: ARS 22,500 to pay. */
function cartWithCoupon() {
  return cart(
    [
      cartItem(),
      cartItem({
        id: 'item-2',
        variantId: 'L-Azul',
        status: 'UNAVAILABLE',
        product: { name: 'Pijama Sol', slug: 'sol', size: 'L', color: 'Azul', sku: 'PP-LAZUL' },
      }),
    ],
    250_000,
    { code: 'PIJAMA10', status: 'APPLICABLE', missingCents: 0 },
  );
}

/** GET answers: this cart and ARS 4,500 of shipping to any province. */
function stubCheckout(currentCart: unknown = cart([cartItem({ quantity: 2 })]), providers = ['MERCADOPAGO']) {
  vi.spyOn(api, 'GET').mockImplementation(((path: string) => {
    if (path === '/api/checkout/payment-providers') {
      return Promise.resolve(answer(200, { providers }));
    }
    if (path === '/api/checkout/shipping-cost') {
      return Promise.resolve(
        answer(200, {
          province: 'SANTA_FE',
          subtotalCents: 5_000_000,
          discountCents: 0,
          shippingCents: 450_000,
          totalCents: 5_450_000,
        }),
      );
    }
    return Promise.resolve(path === '/api/cart' ? currentCart : NO_CART);
  }) as never);
}

async function goToPaymentStep() {
  await screen.findByRole('heading', { name: es.checkout.shippingTitle });
  const values: Record<string, string> = {
    [es.checkout.fields.name]: 'Ana Pérez',
    [es.checkout.fields.email]: 'ana@example.com',
    [es.checkout.fields.phone]: '342 456 7890',
    [es.checkout.fields.nationalId]: '30123456',
    [es.checkout.fields.street]: 'San Martín',
    [es.checkout.fields.number]: '1234',
    [es.checkout.fields.city]: 'Santa Fe',
    [es.checkout.fields.postalCode]: '3000',
  };
  for (const [label, value] of Object.entries(values)) {
    await userEvent.type(screen.getByLabelText(label), value);
  }
  await userEvent.selectOptions(screen.getByLabelText(es.checkout.fields.province), 'SANTA_FE');
  const summary = within(screen.getByRole('complementary', { name: es.checkout.summaryTitle }));
  await waitFor(() => {
    expect(summary.getByText(es.checkout.total).nextElementSibling).toHaveTextContent(/^\$\s54\.500$/);
  });
  await userEvent.click(screen.getByRole('button', { name: es.checkout.toPayment }));
  await screen.findByRole('heading', { name: es.checkout.paymentTitle });
}

async function toReview() {
  await userEvent.click(screen.getByRole('button', { name: es.checkout.toReview }));
  await screen.findByRole('heading', { name: es.checkout.reviewTitle });
}

function trackedOrder(status: OrderStatus, number = 1001): TrackedOrderResponse {
  return {
    number,
    status,
    createdAt: '2026-10-13T17:00:00Z',
    buyerFirstName: 'Ana',
    items: [
      { sku: 'PP-MROSA', productName: 'Pijama Luna', size: 'M', color: 'Rosa', quantity: 2, unitPriceCents: 2_000_000, lineTotalCents: 4_000_000 },
    ],
    subtotalCents: 4_000_000,
    discountCents: 400_000,
    couponCode: 'PIJAMA10',
    shippingCents: 450_000,
    totalCents: 4_050_000,
    city: 'Rosario',
    province: 'SANTA_FE',
    reservationExpiresAt: '2026-10-13T17:30:00Z',
    shipmentTrackingNumber: null,
    canRetryPayment: false,
  };
}

/** The tracked order answers in call order (the last one repeats); the header's cart is empty. */
function stubOrder(...answers: unknown[]) {
  const queue = [...answers];
  vi.spyOn(api, 'GET').mockImplementation(((path: string) =>
    Promise.resolve(path === '/api/cart' ? NO_CART : queue.length > 1 ? queue.shift() : queue[0])) as never);
}

async function openTracking(status: OrderStatus) {
  stubOrder(answer(200, trackedOrder(status)));
  const view = renderRoute(`/pedido/${TOKEN}`);
  await screen.findByText(es.tracking.statuses[status]);
  view.unmount();
  vi.restoreAllMocks();
}

const PURCHASE = {
  transaction_id: '1001',
  event_id: 'purchase-1001',
  currency: 'ARS',
  value: 40500,
  shipping: 4500,
  coupon: 'PIJAMA10',
  items: [{ item_id: 'PP-MROSA', item_name: 'Pijama Luna', item_variant: 'Rosa / M', price: 20000, quantity: 2 }],
};

describe('checkout analytics events (RNF-25, T-E3-20)', { timeout: 30_000 }, () => {
  beforeEach(() => {
    vi.stubEnv('VITE_GA4_MEASUREMENT_ID', 'G-ABC123');
    vi.stubEnv('VITE_META_PIXEL_ID', '1234567890');
    saveConsent('accepted');
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
    resetAnalyticsForTests();
    localStorage.clear();
    document.head.querySelectorAll('script').forEach((script) => {
      script.remove();
    });
  });

  it('given a cart with something to buy, when the checkout opens, then begin_checkout and InitiateCheckout go once with its available items by SKU and the server total', async () => {
    stubCheckout(cartWithCoupon());
    renderRoute('/checkout');

    await screen.findByRole('heading', { name: es.checkout.shippingTitle });

    await waitFor(() => {
      expect(gaEvents('begin_checkout')).toEqual([
        {
          currency: 'ARS',
          value: 22500,
          coupon: 'PIJAMA10',
          items: [{ item_id: 'PP-MROSA', item_name: 'Pijama Luna', item_variant: 'Rosa / M', price: 25000, quantity: 1 }],
        },
      ]);
    });
    expect(pixelCalls('InitiateCheckout')).toEqual([
      [
        {
          content_ids: ['PP-MROSA'],
          content_type: 'product',
          contents: [{ id: 'PP-MROSA', quantity: 1 }],
          num_items: 1,
          value: 22500,
          currency: 'ARS',
        },
      ],
    ]);
  });

  it('given the payment step, when the buyer goes on to the review, back and on again, then add_payment_info and AddPaymentInfo go once with MercadoPago', async () => {
    stubCheckout();
    renderRoute('/checkout');
    await goToPaymentStep();
    expect(gaEvents('add_payment_info')).toEqual([]);

    await toReview();
    await userEvent.click(screen.getByRole('button', { name: es.checkout.editLabel(es.checkout.paymentMethod) }));
    await screen.findByRole('heading', { name: es.checkout.paymentTitle });
    await toReview();

    expect(gaEvents('add_payment_info')).toEqual([
      {
        currency: 'ARS',
        value: 50000,
        payment_type: 'MercadoPago',
        items: [{ item_id: 'PP-MROSA', item_name: 'Pijama Luna', item_variant: 'Rosa / M', price: 25000, quantity: 2 }],
      },
    ]);
    expect(pixelCalls('AddPaymentInfo')).toEqual([
      [
        {
          content_ids: ['PP-MROSA'],
          content_type: 'product',
          contents: [{ id: 'PP-MROSA', quantity: 2 }],
          value: 50000,
          currency: 'ARS',
        },
      ],
    ]);
    expect(gaEvents('begin_checkout')).toHaveLength(1);
  });

  it('given MODO chosen at the payment step, when the buyer goes on to the review, then add_payment_info goes with MODO', async () => {
    stubCheckout(cart([cartItem({ quantity: 2 })]), ['MERCADOPAGO', 'MODO']);
    renderRoute('/checkout');
    await goToPaymentStep();

    await userEvent.click(await screen.findByRole('radio', { name: /^MODO/ }));
    await toReview();

    expect(gaEvents('add_payment_info')).toEqual([expect.objectContaining({ payment_type: 'MODO' })]);
  });

  it('given no consent, when the buyer goes through the checkout, then nothing is sent', async () => {
    localStorage.clear();
    stubCheckout();
    renderRoute('/checkout');
    await goToPaymentStep();
    await toReview();

    expect(window.dataLayer).toBeUndefined();
    expect(window.fbq).toBeUndefined();
  });

  it('given a PAID order, when its tracking page opens, then purchase and Purchase go with the order number and one event id', async () => {
    await openTracking('PAID');

    expect(gaEvents('purchase')).toEqual([PURCHASE]);
    expect(pixelCalls('Purchase')).toEqual([
      [
        {
          content_ids: ['PP-MROSA'],
          content_type: 'product',
          contents: [{ id: 'PP-MROSA', quantity: 2 }],
          num_items: 2,
          value: 40500,
          currency: 'ARS',
        },
        { eventID: 'purchase-1001' },
      ],
    ]);
  });

  it('given a PAID order, when the buyer comes back to the store and reloads the page, then analytics records a single purchase', async () => {
    await openTracking('PAID');
    await openTracking('PAID');
    await openTracking('PREPARING');

    expect(gaEvents('purchase')).toHaveLength(1);
    expect(pixelCalls('Purchase')).toHaveLength(1);
  });

  it.each<OrderStatus>(['PREPARING', 'SHIPPED', 'DELIVERED'])(
    'given an order already %s, when its link opens for the first time, then the purchase is sent',
    async (status) => {
      await openTracking(status);

      expect(gaEvents('purchase')).toEqual([PURCHASE]);
    },
  );

  it.each<OrderStatus>(['AWAITING_PAYMENT', 'CANCELLED', 'EXPIRED'])(
    'given an order %s, when its link opens, then no purchase is sent',
    async (status) => {
      await openTracking(status);

      expect(gaEvents('purchase')).toEqual([]);
      expect(pixelCalls('Purchase')).toEqual([]);
    },
  );

  it('given an approved payment, when the webhook confirms it while the page polls, then the purchase is sent once it is PAID', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    keepTrackingToken(TOKEN);
    stubOrder(answer(200, trackedOrder('AWAITING_PAYMENT')), answer(200, trackedOrder('PAID')));

    renderRoute('/checkout/result?collection_status=approved&status=approved');
    await screen.findByText(es.tracking.confirmingPayment);
    expect(gaEvents('purchase')).toEqual([]);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(CONFIRMING_EVERY_MS);
    });

    await screen.findByText(es.tracking.statuses.PAID);
    expect(gaEvents('purchase')).toEqual([PURCHASE]);
  });

  it('given no consent, when a PAID order opens, then nothing is sent and a later visit after accepting still sends it', async () => {
    localStorage.clear();
    await openTracking('PAID');
    expect(window.dataLayer).toBeUndefined();

    saveConsent('accepted');
    await openTracking('PAID');

    expect(gaEvents('purchase')).toEqual([PURCHASE]);
  });

  it('given a browser without storage, when a purchase is tracked, then it is still sent', () => {
    loadAnalytics({ ga4: 'G-ABC123' });

    trackPurchase(
      { number: 1002, items: [], totalCents: 100, shippingCents: 0, couponCode: null },
      null,
    );

    expect(gaEvents('purchase')).toEqual([
      { transaction_id: '1002', event_id: 'purchase-1002', currency: 'ARS', value: 1, shipping: 0, items: [] },
    ]);
  });

  it('given more orders than the browser remembers, when a new one is marked, then the oldest marks are dropped', () => {
    loadAnalytics({ ga4: 'G-ABC123' });
    const order = (number: number) => ({ number, items: [], totalCents: 100, shippingCents: 0, couponCode: null });

    for (let number = 1; number <= 51; number++) {
      trackPurchase(order(number));
    }
    trackPurchase(order(1));
    trackPurchase(order(51));

    expect(gaEvents('purchase')).toHaveLength(52);
  });
});
