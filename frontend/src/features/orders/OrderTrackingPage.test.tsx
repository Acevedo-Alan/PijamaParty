import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { es } from '../../i18n/es.ts';
import { answer, headerRead, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { clearStoreContact, setStoreContact } from '../../test/storeContact.ts';
import { goToPayment } from './redirect.ts';

vi.mock('./redirect.ts', () => ({ goToPayment: vi.fn() }));

/**
 * MercadoPago's card form (T-E3-29) needs its SDK from the internet; here a stand-in that is ready at once and whose
 * button submits what the real form gives for a test card.
 */
vi.mock('@mercadopago/sdk-react', async () => {
  const { useEffect } = await import('react');
  return {
    initMercadoPago: vi.fn(),
    CardPayment: ({ onSubmit, onReady }: { onSubmit: (form: unknown) => Promise<void>; onReady?: () => void }) => {
      useEffect(() => {
        onReady?.();
      }, [onReady]);
      return (
        <button
          type="button"
          onClick={() =>
            void onSubmit({
              token: 'card-token-1',
              issuer_id: '310',
              payment_method_id: 'visa',
              transaction_amount: 40500,
              installments: 3,
              payer: { email: 'otra@example.com', identification: { type: 'DNI', number: '12345678' } },
            })
          }
        >
          Pagar con la tarjeta de prueba
        </button>
      );
    },
  };
});

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
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) =>
    Promise.resolve(headerRead(path) ?? (queue.length > 1 ? queue.shift() : queue[0]))));
}

const pay = () => screen.getByRole('button', { name: es.tracking.pay });

const PROVIDERS = '/api/checkout/payment-providers';

/** The order's reads in order (the last one repeats), and the card form's public key. */
function stubOrderAndKey(key: string | null, ...answers: unknown[]) {
  const queue = [...answers];
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) =>
    Promise.resolve(path === PROVIDERS
      ? answer(200, { providers: ['MERCADOPAGO'], mercadoPagoPublicKey: key })
      : (headerRead(path) ?? (queue.length > 1 ? queue.shift() : queue[0])))));
}

/** "Pagar" on a MercadoPago order opens the card form on the page; then the given card answer. */
function stubPayOnSite(cardAnswer: unknown) {
  return vi.spyOn(api, 'POST').mockImplementation(((path: string) =>
    Promise.resolve(path === '/api/orders/{token}/retry-payment'
      ? answer(200, { paymentUrl: null, payOnSite: true })
      : cardAnswer)) as never);
}

/** T-E7-50: the chibi beside the payment's message, if any. */
const payChibi = () => document.querySelector('.tracking-pay-feedback svg.chibi');

const sendCard = async () => {
  await userEvent.click(await screen.findByRole('button', { name: 'Pagar con la tarjeta de prueba' }));
};

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

  it('given a pending order, when the page opens, then there is no chibi: only a paid order celebrates (T-E7-48)', async () => {
    stubOrder(answer(200, trackedOrder()));
    renderRoute(`/pedido/${TOKEN}`);

    await screen.findByRole('button', { name: es.tracking.pay });
    expect(document.querySelector('svg.chibi')).toBeNull();
  });

  it('given a pending order, when the buyer taps pay, then it is sent to the payment provider', async () => {
    stubOrder(answer(200, trackedOrder()));
    const post = vi.spyOn(api, 'POST').mockResolvedValue(answer(200, { paymentUrl: PAYMENT_URL }));
    renderRoute(`/pedido/${TOKEN}`);

    await userEvent.click(await screen.findByRole('button', { name: es.tracking.pay }));

    expect(post).toHaveBeenCalledWith('/api/orders/{token}/retry-payment', { params: { path: { token: TOKEN } } });
    expect(goToPayment).toHaveBeenCalledWith(PAYMENT_URL);
  });

  it('given a MercadoPago order, when the buyer taps pay, then the card form opens on the page without leaving the store', async () => {
    stubOrderAndKey('TEST-key', answer(200, trackedOrder()));
    stubPayOnSite(answer(200, { status: 'APPROVED', rejection: null }));
    renderRoute(`/pedido/${TOKEN}`);

    await userEvent.click(await screen.findByRole('button', { name: es.tracking.pay }));

    expect(await screen.findByRole('heading', { name: es.tracking.card.title })).toBeInTheDocument();
    expect(screen.getByText(es.tracking.card.safety)).toBeInTheDocument();
    expect(goToPayment).not.toHaveBeenCalled();
  });

  it('given a MercadoPago order just placed, when its page opens, then the card form is open at once', async () => {
    stubOrderAndKey('TEST-key', answer(200, trackedOrder()));
    renderRoute(`/pedido/${TOKEN}`, { state: { payOnSite: true } });

    expect(await screen.findByRole('heading', { name: es.tracking.card.title })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: es.tracking.pay })).not.toBeInTheDocument();
  });

  it('given an approved card, when it is sent, then it is charged with a key per submit, never an amount, and the paid order shows', async () => {
    stubOrderAndKey('TEST-key', answer(200, trackedOrder()), answer(200, trackedOrder({ status: 'PAID', canRetryPayment: false })));
    const post = stubPayOnSite(answer(200, { status: 'APPROVED', rejection: null }));
    renderRoute(`/pedido/${TOKEN}`, { state: { payOnSite: true } });

    await sendCard();

    expect(await screen.findByText(es.tracking.statuses.PAID)).toBeInTheDocument();
    const [path, options] = post.mock.calls[0] as unknown as [string, { params: { header: Record<string, string> }; body: unknown }];
    expect(path).toBe('/api/orders/{token}/card-payment');
    expect(options.params.header['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(options.body).toEqual({
      cardToken: 'card-token-1',
      paymentMethodId: 'visa',
      issuerId: '310',
      installments: 3,
      identificationType: 'DNI',
      identificationNumber: '12345678',
    });
  });

  it('given a card without funds, when it is sent, then the page says so and the form stays for another card', async () => {
    stubOrderAndKey('TEST-key', answer(200, trackedOrder()));
    stubPayOnSite(answer(200, { status: 'REJECTED', rejection: 'INSUFFICIENT_FUNDS' }));
    renderRoute(`/pedido/${TOKEN}`, { state: { payOnSite: true } });

    await sendCard();

    expect(await screen.findByText(es.tracking.card.rejections.INSUFFICIENT_FUNDS)).toBeInTheDocument();
    expect(screen.getByText(es.tracking.card.rejections.INSUFFICIENT_FUNDS).closest('.alert-box')).not.toBeNull();
    expect(screen.getByRole('button', { name: 'Pagar con la tarjeta de prueba' })).toBeInTheDocument();
  });

  it('given a card left in review, when it is sent, then the page says the payment is in review', async () => {
    stubOrderAndKey('TEST-key', answer(200, trackedOrder()));
    stubPayOnSite(answer(200, { status: 'IN_PROCESS', rejection: null }));
    renderRoute(`/pedido/${TOKEN}`, { state: { payOnSite: true } });

    await sendCard();

    expect(await screen.findByText(es.tracking.card.inProcess)).toBeInTheDocument();
  });

  it('given the card form open, when nothing is under way, then there is no chibi beside it (T-E7-50)', async () => {
    stubOrderAndKey('TEST-key', answer(200, trackedOrder()));
    renderRoute(`/pedido/${TOKEN}`, { state: { payOnSite: true } });

    await screen.findByRole('button', { name: 'Pagar con la tarjeta de prueba' });
    expect(payChibi()).toBeNull();
  });

  it('given a card sent, when the API has not answered yet, then the chibi waits with its stars beside "Procesando tu pago…" (T-E7-50)', async () => {
    stubOrderAndKey('TEST-key', answer(200, trackedOrder()));
    vi.spyOn(api, 'POST').mockImplementation((() => new Promise(() => undefined)) as never);
    renderRoute(`/pedido/${TOKEN}`, { state: { payOnSite: true } });

    await sendCard();

    const charging = await screen.findByText(es.tracking.card.charging);
    expect(charging).toHaveAttribute('role', 'status');
    expect(payChibi()).toHaveAttribute('data-mood', 'waiting');
    expect(payChibi()).toHaveAttribute('data-pose', 'paying');
    expect(payChibi()).toHaveAttribute('aria-hidden', 'true');
    expect(payChibi()?.querySelectorAll('.chibi-orbit path')).toHaveLength(3);
  });

  it('given a card left in review, when the API answers, then the chibi keeps waiting beside the message (T-E7-50)', async () => {
    stubOrderAndKey('TEST-key', answer(200, trackedOrder()));
    stubPayOnSite(answer(200, { status: 'IN_PROCESS', rejection: null }));
    renderRoute(`/pedido/${TOKEN}`, { state: { payOnSite: true } });

    await sendCard();

    await screen.findByText(es.tracking.card.inProcess);
    expect(payChibi()).toHaveAttribute('data-mood', 'waiting');
  });

  it('given a rejected card, when the API answers, then the chibi is sad beside the reason and the form stays (T-E7-50)', async () => {
    stubOrderAndKey('TEST-key', answer(200, trackedOrder()));
    stubPayOnSite(answer(200, { status: 'REJECTED', rejection: 'INSUFFICIENT_FUNDS' }));
    renderRoute(`/pedido/${TOKEN}`, { state: { payOnSite: true } });

    await sendCard();

    await screen.findByText(es.tracking.card.rejections.INSUFFICIENT_FUNDS);
    expect(payChibi()).toHaveAttribute('data-mood', 'error');
    expect(payChibi()?.querySelector('.chibi-orbit')).toBeNull();
    expect(screen.getByRole('button', { name: 'Pagar con la tarjeta de prueba' })).toBeInTheDocument();
  });

  it('given an approved return, when the pending order opens, then the chibi waits beside "Estamos confirmando tu pago" (T-E7-50)', async () => {
    stubOrder(answer(200, trackedOrder()));
    renderRoute(`/pedido/${TOKEN}`, { state: { paymentApproved: true } });

    await screen.findByText(es.tracking.confirmingPayment);
    expect(payChibi()).toHaveAttribute('data-mood', 'waiting');
    expect(payChibi()).toHaveAttribute('data-pose', 'paying');
  });

  it('given a store without the card form configured, when it should open, then the page says card payment is unavailable', async () => {
    stubOrderAndKey(null, answer(200, trackedOrder()));
    renderRoute(`/pedido/${TOKEN}`, { state: { payOnSite: true } });

    expect(await screen.findByText(es.tracking.card.unavailable)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pagar con la tarjeta de prueba' })).not.toBeInTheDocument();
  });

  it('given the payment provider down, when the buyer taps pay, then it is asked to try again in a moment and stays', async () => {
    stubOrder(answer(200, trackedOrder()));
    vi.spyOn(api, 'POST').mockResolvedValue(answer(503));
    renderRoute(`/pedido/${TOKEN}`);

    await userEvent.click(await screen.findByRole('button', { name: es.tracking.pay }));

    expect(await screen.findByRole('alert')).toHaveTextContent(es.tracking.providerUnavailable);
    expect(goToPayment).not.toHaveBeenCalled();
    expect(pay()).toBeEnabled();
  });

  it('given a payment retry the provider does not answer, when the error shows, then it is an alert box with a warning icon', async () => {
    stubOrder(answer(200, trackedOrder()));
    vi.spyOn(api, 'POST').mockResolvedValue(answer(503));
    renderRoute(`/pedido/${TOKEN}`);

    await userEvent.click(await screen.findByRole('button', { name: es.tracking.pay }));

    const box = (await screen.findByText(es.tracking.providerUnavailable)).closest('.alert-box');
    expect(box).not.toBeNull();
    expect(box?.querySelector('svg[aria-hidden="true"]')).not.toBeNull();
  });

  it('given an item whose product has a photo, when the tracking link opens, then the item shows its thumbnail', async () => {
    const photo = { placeholder: 'data:image/webp;base64,UklGRg==', files: [{ width: 400, url: '/uploads/luna-rosa-400.webp' }] };
    const [item] = trackedOrder().items ?? [];
    stubOrder(answer(200, trackedOrder({ items: [{ ...item, image: photo }] })));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByRole('img', { name: es.cart.photoAlt('Pijama Luna', 'Rosa') })).toHaveAttribute(
      'src',
      '/uploads/luna-rosa-400.webp',
    );
  });

  it('given an item whose product has no photo, when the tracking link opens, then the item shows the placeholder', async () => {
    stubOrder(answer(200, trackedOrder()));

    renderRoute(`/pedido/${TOKEN}`);

    const name = await screen.findByText('Pijama Luna');
    expect(name.closest('li')?.querySelector('.ph.tracking-item-photo')).not.toBeNull();
    expect(screen.queryByRole('img', { name: es.cart.photoAlt('Pijama Luna', 'Rosa') })).not.toBeInTheDocument();
  });

  it('given an order of "alan" in "santa fe", when the tracking link opens, then it greets "Alan" and ships to "Santa Fe"', async () => {
    stubOrder(answer(200, trackedOrder({ buyerFirstName: 'alan', city: 'santa fe' })));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByText(es.tracking.greeting('Alan'))).toBeInTheDocument();
    expect(screen.getByText(es.tracking.shipTo('Santa Fe', 'Santa Fe'))).toBeInTheDocument();
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

describe('the purchase confirmation (T-E7-44)', () => {
  const paidOrder = (overrides: Partial<TrackedOrderResponse> = {}) =>
    trackedOrder({ status: 'PAID', canRetryPayment: false, buyerFirstName: 'alan', city: 'santa fe', ...overrides });

  afterEach(() => {
    vi.restoreAllMocks();
    clearStoreContact();
    Reflect.deleteProperty(navigator, 'clipboard');
  });

  const step = (name: string) => screen.getByText(name).closest('li');

  it('given a paid order, when the page opens, then the chibi celebrates with the check as a seal, both hidden from screen readers (T-E7-48)', async () => {
    stubOrder(answer(200, paidOrder()));

    renderRoute(`/pedido/${TOKEN}`);

    const heading = await screen.findByRole('heading', { level: 1, name: es.tracking.paid.thanks('Alan') });
    const stage = heading.parentElement?.querySelector('.tracking-paid-chibi');
    const chibi = stage?.querySelector('svg.chibi');
    expect(chibi).toHaveAttribute('data-mood', 'success');
    expect(chibi).toHaveAttribute('data-pose', 'paid');
    expect(chibi).toHaveAttribute('aria-hidden', 'true');
    expect(chibi?.querySelectorAll('.chibi-confetti path')).toHaveLength(6);
    expect(stage?.querySelector('.tracking-paid-check')).toHaveAttribute('aria-hidden', 'true');
  });

  it('given a paid order of "alan", when the page opens, then it thanks the buyer with the order number and "Pagado" as the current step', async () => {
    stubOrder(answer(200, paidOrder()));

    renderRoute(`/pedido/${TOKEN}`);

    const heading = await screen.findByRole('heading', { level: 1, name: es.tracking.paid.thanks('Alan') });
    // T-E7-52: the celebrating kaomoji is drawn after the heading, outside its name.
    expect(heading).toHaveAttribute('data-kaomoji', es.kaomoji.paid);
    expect(screen.getByText(es.tracking.paid.lines.PAID)).toBeInTheDocument();
    expect(screen.getByText(es.tracking.title(1001))).toBeInTheDocument();
    expect(screen.queryByText(es.tracking.greeting('Alan'))).not.toBeInTheDocument();
    expect(step(es.tracking.statuses.PAID)).toHaveAttribute('aria-current', 'step');
    expect(step(es.tracking.statuses.PREPARING)).not.toHaveAttribute('aria-current');
  });

  it('given a pending, cancelled or expired order, when the page opens, then it does not thank the buyer', async () => {
    stubOrder(answer(200, trackedOrder({ status: 'EXPIRED', canRetryPayment: false })));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByRole('heading', { level: 1, name: es.tracking.title(1001) })).toBeInTheDocument();
    expect(screen.queryByText(/Gracias por tu compra/)).not.toBeInTheDocument();
    expect(screen.queryByRole('list', { name: es.tracking.paid.progress })).not.toBeInTheDocument();
  });

  it('given a shipped order, when the progress is read, then paid and preparing are completed and shipped is current', async () => {
    stubOrder(answer(200, paidOrder({ status: 'SHIPPED', shipmentTrackingNumber: 'CA123456789AR' })));

    renderRoute(`/pedido/${TOKEN}`);

    await screen.findByRole('list', { name: es.tracking.paid.progress });
    expect(step(es.tracking.statuses.PAID)).toHaveTextContent(es.tracking.paid.completed);
    expect(step(es.tracking.statuses.PREPARING)).toHaveTextContent(es.tracking.paid.completed);
    expect(step(es.tracking.statuses.SHIPPED)).toHaveAttribute('aria-current', 'step');
    expect(step(es.tracking.statuses.DELIVERED)).not.toHaveAttribute('aria-current');
    expect(step(es.tracking.statuses.DELIVERED)).not.toHaveTextContent(es.tracking.paid.completed);
    expect(screen.getByText(es.tracking.paid.lines.SHIPPED)).toBeInTheDocument();
    expect(screen.queryByText(es.tracking.paid.arrivesInSantaFe)).not.toBeInTheDocument();
  });

  it('given a paid order to Santa Fe, when the page opens, then delivery is by Correo Argentino in 1 to 3 working days', async () => {
    stubOrder(answer(200, paidOrder()));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByText(es.tracking.paid.shipsTo('Santa Fe', 'Santa Fe'))).toBeInTheDocument();
    expect(screen.getByText(es.tracking.paid.arrivesInSantaFe)).toBeInTheDocument();
    expect(screen.getByText(es.tracking.paid.emailSent)).toBeInTheDocument();
    expect(screen.queryByText(es.tracking.shipTo('Santa Fe', 'Santa Fe'))).not.toBeInTheDocument();
  });

  it('given a paid order to Córdoba, when the page opens, then delivery takes 3 to 7 working days', async () => {
    stubOrder(answer(200, paidOrder({ city: 'córdoba', province: 'CORDOBA' })));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByText(es.tracking.paid.arrivesElsewhere)).toBeInTheDocument();
    expect(screen.queryByText(es.tracking.paid.arrivesInSantaFe)).not.toBeInTheDocument();
  });

  it('given the clipboard, when "Copiar" is pressed, then the order number is copied and the button says so', async () => {
    const writeText = vi.fn(() => Promise.resolve());
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    stubOrder(answer(200, paidOrder()));

    renderRoute(`/pedido/${TOKEN}`);
    await userEvent.click(await screen.findByRole('button', { name: es.tracking.paid.copyLabel(1001) }));

    expect(writeText).toHaveBeenCalledWith('#1001');
    expect(await screen.findByRole('button', { name: es.tracking.paid.copied })).toBeInTheDocument();
  });

  it('given a browser without the clipboard, when the page opens, then there is no "Copiar"', async () => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    stubOrder(answer(200, paidOrder()));

    renderRoute(`/pedido/${TOKEN}`);

    await screen.findByText(es.tracking.title(1001));
    expect(screen.queryByRole('button', { name: es.tracking.paid.copyLabel(1001) })).not.toBeInTheDocument();
  });

  it('given the store WhatsApp and Instagram, when the page opens, then WhatsApp carries the order number and the closing links the store and Instagram', async () => {
    setStoreContact({ whatsAppNumber: '5493425304507', instagramUrl: 'https://www.instagram.com/pijamaparty.zzz/' });
    stubOrder(answer(200, paidOrder()));

    renderRoute(`/pedido/${TOKEN}`);

    const whatsApp = await screen.findByRole('link', { name: es.tracking.whatsApp });
    expect(whatsApp).toHaveAttribute(
      'href',
      `https://wa.me/5493425304507?text=${encodeURIComponent(es.tracking.whatsAppMessage(1001))}`,
    );
    expect(whatsApp).toHaveAttribute('target', '_blank');
    expect(screen.getByRole('link', { name: es.tracking.instagram })).toHaveAttribute(
      'href',
      'https://www.instagram.com/pijamaparty.zzz/',
    );
    expect(screen.getAllByRole('link', { name: es.tracking.backToStore })[0]).toHaveAttribute('href', '/');
  });

  it('given a store without WhatsApp or Instagram, when the page opens, then neither link shows', async () => {
    stubOrder(answer(200, paidOrder()));

    renderRoute(`/pedido/${TOKEN}`);

    await screen.findByText(es.tracking.title(1001));
    expect(screen.queryByRole('link', { name: es.tracking.whatsApp })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: es.tracking.instagram })).not.toBeInTheDocument();
  });
});

describe('email correction (UC-17, T-E3-23)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  const openForm = async () => {
    await userEvent.click(await screen.findByRole('button', { name: es.tracking.emailCorrection.open }));
  };

  const save = async (email: string) => {
    await userEvent.type(screen.getByLabelText(es.tracking.emailCorrection.label), email);
    await userEvent.click(screen.getByRole('button', { name: es.tracking.emailCorrection.save }));
  };

  it('given a paid order, when the buyer corrects its email, then it is told the confirmation was sent again there', async () => {
    stubOrder(answer(200, trackedOrder({ status: 'PAID', canRetryPayment: false })));
    const patch = vi.spyOn(api, 'PATCH').mockResolvedValue(answer(200, { confirmationResent: true }));
    renderRoute(`/pedido/${TOKEN}`);

    await openForm();
    await save(' ana.perez@gmail.com ');

    expect(patch).toHaveBeenCalledWith('/api/orders/{token}/email', {
      params: { path: { token: TOKEN } },
      body: { email: 'ana.perez@gmail.com' },
    });
    expect(await screen.findByRole('status')).toHaveTextContent(es.tracking.emailCorrection.resent('ana.perez@gmail.com'));
    expect(screen.queryByLabelText(es.tracking.emailCorrection.label)).not.toBeInTheDocument();
    // WCAG 2.4.3: focus returns to the button when the form closes
    expect(screen.getByRole('button', { name: es.tracking.emailCorrection.open })).toHaveFocus();
  });

  it('given an order awaiting payment, when the buyer corrects its email, then it is told the confirmation will go there once paid', async () => {
    stubOrder(answer(200, trackedOrder()));
    vi.spyOn(api, 'PATCH').mockResolvedValue(answer(200, { confirmationResent: false }));
    renderRoute(`/pedido/${TOKEN}`);

    await openForm();
    await save('ana.perez@gmail.com');

    expect(await screen.findByText(es.tracking.emailCorrection.savedForLater('ana.perez@gmail.com'))).toBeInTheDocument();
  });

  it('given the form open, when it is still empty, then it cannot be saved', async () => {
    stubOrder(answer(200, trackedOrder({ status: 'PAID', canRetryPayment: false })));
    renderRoute(`/pedido/${TOKEN}`);

    await openForm();

    expect(screen.getByLabelText(es.tracking.emailCorrection.label)).toHaveValue('');
    expect(screen.getByRole('button', { name: es.tracking.emailCorrection.save })).toBeDisabled();
  });

  it('given an email the server refuses, when saved, then the buyer is asked to check it and the form stays', async () => {
    stubOrder(answer(200, trackedOrder({ status: 'PAID', canRetryPayment: false })));
    vi.spyOn(api, 'PATCH').mockResolvedValue(answer(422));
    renderRoute(`/pedido/${TOKEN}`);

    await openForm();
    await save('ana.perez@');

    expect(await screen.findByRole('alert')).toHaveTextContent(es.tracking.emailCorrection.invalid);
    expect(screen.getByLabelText(es.tracking.emailCorrection.label)).toHaveValue('ana.perez@');
  });

  it('given more than 3 corrections in an hour, when another is saved, then the buyer is asked to try later', async () => {
    stubOrder(answer(200, trackedOrder({ status: 'PAID', canRetryPayment: false })));
    vi.spyOn(api, 'PATCH').mockResolvedValue(answer(429));
    renderRoute(`/pedido/${TOKEN}`);

    await openForm();
    await save('ana.perez@gmail.com');

    expect(await screen.findByRole('alert')).toHaveTextContent(es.tracking.emailCorrection.tooManyAttempts);
  });

  it('given an order that expired meanwhile, when the email is saved, then the order is read again and the form is gone', async () => {
    stubOrder(answer(200, trackedOrder()), answer(200, trackedOrder({ status: 'EXPIRED', canRetryPayment: false })));
    vi.spyOn(api, 'PATCH').mockResolvedValue(answer(409));
    renderRoute(`/pedido/${TOKEN}`);

    await openForm();
    await save('ana.perez@gmail.com');

    expect(await screen.findByText(es.tracking.statuses.EXPIRED)).toBeInTheDocument();
    expect(screen.queryByLabelText(es.tracking.emailCorrection.label)).not.toBeInTheDocument();
  });

  it('given a cancelled or expired order, when its link opens, then the email cannot be corrected', async () => {
    stubOrder(answer(200, trackedOrder({ status: 'CANCELLED', canRetryPayment: false })));

    renderRoute(`/pedido/${TOKEN}`);

    expect(await screen.findByText(es.tracking.statuses.CANCELLED)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: es.tracking.emailCorrection.open })).not.toBeInTheDocument();
  });
});
