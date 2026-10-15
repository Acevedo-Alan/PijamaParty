import { act, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { es } from '../../i18n/es.ts';
import { answer, NO_CART } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { CONFIRMING_FOR_MS } from '../orders/OrderTrackingPage.tsx';
import { CONFIRMING_EVERY_MS } from '../orders/useTrackedOrder.ts';
import { keepTrackingToken } from './trackingTokenStore.ts';

type TrackedOrderResponse = components['schemas']['TrackedOrderResponse'];

const TOKEN = 'c'.repeat(43);

function trackedOrder(status: NonNullable<TrackedOrderResponse['status']>): TrackedOrderResponse {
  return {
    number: 1001,
    status,
    createdAt: '2026-10-13T17:00:00Z',
    buyerFirstName: 'Ana',
    items: [],
    subtotalCents: 2_500_000,
    discountCents: 0,
    couponCode: null,
    shippingCents: 450_000,
    totalCents: 2_950_000,
    city: 'Santa Fe',
    province: 'SANTA_FE',
    reservationExpiresAt: '2026-10-13T17:30:00Z',
    shipmentTrackingNumber: null,
    canRetryPayment: true,
  };
}

/** The tracked order answers in call order (the last one repeats); the header's cart is empty. */
function stubOrder(...answers: unknown[]) {
  const queue = [...answers];
  return vi.spyOn(api, 'GET').mockImplementation(((path: string) =>
    Promise.resolve(path === '/api/cart' ? NO_CART : queue.length > 1 ? queue.shift() : queue[0])) as never);
}

const orderCalls = (get: ReturnType<typeof stubOrder>) =>
  (get.mock.calls as unknown[][]).filter(([path]) => path === '/api/orders/{token}').length;

describe('return from MercadoPago (UC-14, T-E3-11)', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('given the order placed in this tab, when the buyer comes back, then its tracking page opens', async () => {
    keepTrackingToken(TOKEN);
    stubOrder(answer(200, trackedOrder('AWAITING_PAYMENT')));

    const { router } = renderRoute('/checkout/result?collection_status=rejected&status=rejected');

    expect(router.state.location.pathname).toBe(`/pedido/${TOKEN}`);
    expect(await screen.findByRole('button', { name: es.tracking.pay })).toBeEnabled();
  });

  it('given an approved payment not confirmed yet, when the buyer comes back, then the page says it is confirming and reads the order again until it is paid', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    keepTrackingToken(TOKEN);
    const get = stubOrder(answer(200, trackedOrder('AWAITING_PAYMENT')), answer(200, trackedOrder('PAID')));

    renderRoute('/checkout/result?collection_status=approved&status=approved');

    expect(await screen.findByText(es.tracking.confirmingPayment)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: es.tracking.pay })).not.toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(CONFIRMING_EVERY_MS);
    });

    expect(await screen.findByText(es.tracking.statuses.PAID)).toBeInTheDocument();
    expect(screen.queryByText(es.tracking.confirmingPayment)).not.toBeInTheDocument();
    const calls = orderCalls(get);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(CONFIRMING_EVERY_MS * 3);
    });
    expect(orderCalls(get)).toBe(calls);
  });

  it('given an approved payment that is never confirmed, when 2 minutes pass, then the usual page comes back', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    keepTrackingToken(TOKEN);
    stubOrder(answer(200, trackedOrder('AWAITING_PAYMENT')));

    renderRoute('/checkout/result?collection_status=approved');
    expect(await screen.findByText(es.tracking.confirmingPayment)).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(CONFIRMING_FOR_MS);
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: es.tracking.pay })).toBeEnabled();
    });
  });

  it('given no order placed in this tab, when the buyer comes back, then it is told the confirmation arrives by email', async () => {
    stubOrder(answer(404));

    renderRoute('/checkout/result?collection_status=approved');

    expect(await screen.findByText(es.checkoutResult.noOrder)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: es.checkoutResult.backToStore })).toHaveAttribute('href', '/');
  });
});

describe('return from MODO (UC-14, T-E3b-04)', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given an approved MODO payment not confirmed yet, when the buyer comes back to callbackSuccess, then the page says it is confirming', async () => {
    keepTrackingToken(TOKEN);
    stubOrder(answer(200, trackedOrder('AWAITING_PAYMENT')));

    const { router } = renderRoute('/checkout/result?status=approved');

    expect(router.state.location.pathname).toBe(`/pedido/${TOKEN}`);
    expect(await screen.findByText(es.tracking.confirmingPayment)).toBeInTheDocument();
  });

  it('given a MODO payment not made, when the buyer comes back to callback, then the order can be paid again', async () => {
    keepTrackingToken(TOKEN);
    stubOrder(answer(200, trackedOrder('AWAITING_PAYMENT')));

    renderRoute('/checkout/result?status=failure');

    expect(await screen.findByRole('button', { name: es.tracking.pay })).toBeEnabled();
    expect(screen.queryByText(es.tracking.confirmingPayment)).not.toBeInTheDocument();
  });
});
