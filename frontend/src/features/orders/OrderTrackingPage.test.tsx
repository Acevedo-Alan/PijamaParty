import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { es } from '../../i18n/es.ts';
import { answer, NO_CART } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { goToPayment } from './redirect.ts';

vi.mock('./redirect.ts', () => ({ goToPayment: vi.fn() }));

type TrackedOrderResponse = components['schemas']['TrackedOrderResponse'];

const TOKEN = 'a'.repeat(43);
const PAYMENT_URL = 'https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=1';

function trackedOrder(overrides: Partial<TrackedOrderResponse> = {}): TrackedOrderResponse {
  return {
    number: 1001,
    status: 'AWAITING_PAYMENT',
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
    canRetryPayment: true,
    ...overrides,
  };
}

/** Answers the tracked order calls in order (the last one repeats); the header's cart is empty. */
function stubOrder(...answers: unknown[]) {
  const queue = [...answers];
  return vi.spyOn(api, 'GET').mockImplementation(((path: string) =>
    Promise.resolve(path === '/api/cart' ? NO_CART : queue.length > 1 ? queue.shift() : queue[0])) as never);
}

const pay = () => screen.getByRole('button', { name: es.tracking.pay });

describe('tracking link (UC-14, T-E3-08)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.mocked(goToPayment).mockClear();
  });

  it('given a buyer back without paying, when it opens its tracking link, then the order shows pending with the option to retry', async () => {
    const get = stubOrder(answer(200, trackedOrder()));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByRole('heading', { level: 1, name: es.tracking.title(1001) })).toBeInTheDocument();
    expect(screen.getByText(es.tracking.greeting('Ana'))).toBeInTheDocument();
    expect(screen.getByText(es.tracking.statuses.AWAITING_PAYMENT)).toBeInTheDocument();
    expect(screen.getByText(es.tracking.reservedUntil('14:30'))).toBeInTheDocument();
    expect(pay()).toBeEnabled();
    expect(get).toHaveBeenCalledWith('/api/orders/{token}', expect.objectContaining({
      params: { path: { token: TOKEN } },
    }));
  });

  it('given an order, when its tracking link opens, then its items, amounts and destination are shown', async () => {
    stubOrder(answer(200, trackedOrder()));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByText('Pijama Luna')).toBeInTheDocument();
    expect(screen.getByText(es.tracking.itemDetail('Rosa', 'M', 2))).toBeInTheDocument();
    expect(screen.getByText(es.tracking.discountWith('PIJAMA10'))).toBeInTheDocument();
    expect(screen.getByText(/^\$\s40\.500$/)).toBeInTheDocument();
    expect(screen.getByText(es.tracking.shipTo('Rosario', 'Santa Fe'))).toBeInTheDocument();
  });

  it('given a pending order, when the buyer taps pay, then it is sent to the payment provider', async () => {
    stubOrder(answer(200, trackedOrder()));
    const post = vi.spyOn(api, 'POST').mockResolvedValue(answer(200, { paymentUrl: PAYMENT_URL }));
    renderRoute(`/pedido/${TOKEN}`);

    await userEvent.click(await screen.findByRole('button', { name: es.tracking.pay }));

    expect(post).toHaveBeenCalledWith('/api/orders/{token}/retry-payment', { params: { path: { token: TOKEN } } });
    expect(goToPayment).toHaveBeenCalledWith(PAYMENT_URL);
  });

  it('given MercadoPago down, when the buyer taps pay, then it is asked to try again in a moment and stays', async () => {
    stubOrder(answer(200, trackedOrder()));
    vi.spyOn(api, 'POST').mockResolvedValue(answer(503));
    renderRoute(`/pedido/${TOKEN}`);

    await userEvent.click(await screen.findByRole('button', { name: es.tracking.pay }));

    expect(await screen.findByRole('alert')).toHaveTextContent(es.tracking.providerUnavailable);
    expect(goToPayment).not.toHaveBeenCalled();
    expect(pay()).toBeEnabled();
  });

  it('given an order whose reservation ended meanwhile, when the buyer taps pay, then the order is read again and says so', async () => {
    stubOrder(answer(200, trackedOrder()), answer(200, trackedOrder({ status: 'EXPIRED', canRetryPayment: false })));
    vi.spyOn(api, 'POST').mockResolvedValue(answer(409));
    renderRoute(`/pedido/${TOKEN}`);

    await userEvent.click(await screen.findByRole('button', { name: es.tracking.pay }));

    expect(await screen.findByText(es.tracking.statuses.EXPIRED)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: es.tracking.pay })).not.toBeInTheDocument();
    expect(goToPayment).not.toHaveBeenCalled();
  });

  it('given an order awaiting payment whose reservation is over, when its link opens, then it says so without paying', async () => {
    stubOrder(answer(200, trackedOrder({ canRetryPayment: false })));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByText(es.tracking.reservationOver)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: es.tracking.pay })).not.toBeInTheDocument();
  });

  it('given a shipped order, when its link opens, then it shows the shipment tracking number and no payment', async () => {
    stubOrder(answer(200, trackedOrder({ status: 'SHIPPED', canRetryPayment: false, shipmentTrackingNumber: 'CA123456789AR' })));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByText('CA123456789AR')).toBeInTheDocument();
    expect(screen.getByText(es.tracking.shipment)).toBeInTheDocument();
    expect(screen.getByText(es.tracking.statuses.SHIPPED)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: es.tracking.pay })).not.toBeInTheDocument();
  });

  it('given a token of no order, when its link opens, then the order is not found with a way back to the store', async () => {
    stubOrder(answer(404));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByRole('heading', { level: 1, name: es.tracking.notFoundTitle })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: es.tracking.backToStore })).toHaveAttribute('href', '/');
  });
});
