import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { answer, NO_CART, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { CORREO_TRACKING_URL } from './myOrdersApi.ts';

const messages = es.account.orders;
const ANA = { id: '00000000-0000-0000-0000-000000000001', email: 'ana@example.com', name: 'Ana Pérez' };
const FIRST = '00000000-0000-0000-0000-00000000a001';
const SECOND = '00000000-0000-0000-0000-00000000a002';

function summary(id: string, number: number, createdAt: string, status: string, totalCents: number) {
  return { id, number, createdAt, status, totalCents };
}

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: SECOND,
    number: 1002,
    status: 'SHIPPED',
    createdAt: '2026-10-14T15:00:00Z',
    items: [
      {
        sku: 'PL-M-ROSA',
        productName: 'Pijama Luna',
        size: 'M',
        color: 'Rosa',
        quantity: 2,
        unitPriceCents: 2_000_000,
        lineTotalCents: 4_000_000,
      },
    ],
    subtotalCents: 4_000_000,
    discountCents: 400_000,
    couponCode: 'VERANO10',
    shippingCents: 450_000,
    totalCents: 4_050_000,
    address: {
      street: 'San Martín',
      number: '1234',
      floor: '3',
      apartment: 'B',
      city: 'Santa Fe',
      province: 'SANTA_FE',
      postalCode: 'S3000ABC',
    },
    shipmentTrackingNumber: 'CA123456789AR',
    paymentProvider: 'MERCADOPAGO',
    ...overrides,
  };
}

type Init = { params?: { path?: { id?: string }; query?: { page?: number } } } | undefined;

/** The customer is signed in; `orders` answers GET /api/account/orders and `order` the detail. */
function stubOrders(orders: (page: number) => unknown, order: (id: string) => unknown = () => answer(404)) {
  return vi.spyOn(api, 'GET').mockImplementation(
    withHeader((path, init?: Init) => {
      if (path === '/api/account/orders') {
        return Promise.resolve(orders(init?.params?.query?.page ?? 0));
      }
      if (path === '/api/account/orders/{id}') {
        return Promise.resolve(order(init?.params?.path?.id ?? ''));
      }
      return Promise.resolve(NO_CART);
    }, answer(200, ANA)),
  );
}

describe('my orders (UC-22, T-E4-07)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a visitor who is not signed in, when it opens my orders, then it is sent to sign in', async () => {
    vi.spyOn(api, 'GET').mockImplementation(withHeader(() => Promise.resolve(NO_CART)));
    const { router } = renderRoute('/cuenta/pedidos');

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/cuenta/ingresar');
    });
  });

  it('given a customer with two orders, when it follows the header link, then it sees both newest first with status and total', async () => {
    stubOrders(() =>
      answer(200, {
        orders: [
          summary(SECOND, 1002, '2026-10-14T15:00:00Z', 'SHIPPED', 4_050_000),
          summary(FIRST, 1001, '2026-10-13T15:00:00Z', 'AWAITING_PAYMENT', 2_450_000),
        ],
        hasMore: false,
      }),
    );
    const { router } = renderRoute('/');

    await userEvent.click(await screen.findByRole('button', { name: es.account.menu }));
    await userEvent.click(await screen.findByRole('link', { name: es.account.ordersLink }));

    expect(router.state.location.pathname).toBe('/cuenta/pedidos');
    const items = await within(screen.getByRole('main')).findAllByRole('article');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Pedido #1002');
    expect(items[0]).toHaveTextContent(es.tracking.statuses.SHIPPED);
    expect(items[0]).toHaveTextContent('$ 40.500');
    expect(items[0]).toHaveTextContent(messages.placedOnShort('14/10/2026'));
    expect(items[1]).toHaveTextContent('Pedido #1001');
    expect(items[1]).toHaveTextContent(es.tracking.statuses.AWAITING_PAYMENT);
    expect(screen.queryByRole('button', { name: messages.more })).not.toBeInTheDocument();
  });

  it('given more orders than a page, when the customer taps "Ver más", then the next page is added', async () => {
    const get = stubOrders((page) =>
      page === 0
        ? answer(200, { orders: [summary(SECOND, 1002, '2026-10-14T15:00:00Z', 'PAID', 100)], hasMore: true })
        : answer(200, { orders: [summary(FIRST, 1001, '2026-10-13T15:00:00Z', 'PAID', 100)], hasMore: false }),
    );
    renderRoute('/cuenta/pedidos');

    await userEvent.click(await screen.findByRole('button', { name: messages.more }));

    await waitFor(() => {
      expect(within(screen.getByRole('main')).getAllByRole('article')).toHaveLength(2);
    });
    expect(within(screen.getByRole('main')).getAllByRole('article')[1]).toHaveTextContent('Pedido #1001');
    expect(screen.queryByRole('button', { name: messages.more })).not.toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/account/orders', expect.objectContaining({ params: { query: { page: 1 } } }));
  });

  it('given a customer without orders, when it opens my orders, then it is invited to the store', async () => {
    stubOrders(() => answer(200, { orders: [], hasMore: false }));
    renderRoute('/cuenta/pedidos');

    expect(await screen.findByText(messages.empty)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.goShopping })).toHaveAttribute('href', '/');
  });

  it('given a customer without orders, when it opens my orders, then the chibi holds an empty bag above the text, hidden from screen readers (T-E7-49)', async () => {
    stubOrders(() => answer(200, { orders: [], hasMore: false }));
    renderRoute('/cuenta/pedidos');

    const chibi = (await screen.findByText(messages.empty)).previousElementSibling;
    expect(chibi).toHaveClass('chibi');
    expect(chibi).toHaveAttribute('aria-hidden', 'true');
    expect(chibi).toHaveAttribute('data-mood', 'idle');
    expect(chibi).toHaveAttribute('data-pose', 'orders');
    expect(chibi?.querySelector('.chibi-bag')).not.toBeNull();
  });

  it('given a shipped order, when the customer opens it, then it sees items, address, status, tracking number and a link to Correo Argentino', async () => {
    stubOrders(
      () => answer(200, { orders: [summary(SECOND, 1002, '2026-10-14T15:00:00Z', 'SHIPPED', 4_050_000)], hasMore: false }),
      (id) => (id === SECOND ? answer(200, detail()) : answer(404)),
    );
    const user = userEvent.setup();
    const { router } = renderRoute('/cuenta/pedidos');

    await user.click(await screen.findByRole('link', { name: `${messages.viewDetail} · Pedido #1002` }));

    expect(router.state.location.pathname).toBe(`/cuenta/pedidos/${SECOND}`);
    expect(await screen.findByRole('heading', { name: 'Pedido #1002' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: messages.shipment })).toHaveTextContent(es.tracking.statuses.SHIPPED);
    const items = screen.getByRole('region', { name: messages.items });
    expect(within(items).getByText('Pijama Luna')).toBeInTheDocument();
    expect(within(items).getByText(es.tracking.discountWith('VERANO10'))).toBeInTheDocument();
    expect(within(items).getByText('$ 40.500')).toBeInTheDocument();
    const address = screen.getByRole('region', { name: messages.address });
    expect(address).toHaveTextContent('San Martín 1234, piso 3, depto. B');
    expect(address).toHaveTextContent('Santa Fe, Santa Fe (S3000ABC)');
    const shipment = screen.getByRole('region', { name: messages.shipment });
    expect(shipment).toHaveTextContent('CA123456789AR');
    const correo = within(shipment).getByRole('link', { name: messages.trackAtCorreo });
    expect(correo).toHaveAttribute('href', CORREO_TRACKING_URL);
    expect(correo).toHaveAttribute('target', '_blank');
    expect(correo).toHaveAttribute('rel', 'noopener noreferrer');

    await user.click(within(shipment).getByRole('button', { name: messages.copy }));

    expect(await navigator.clipboard.readText()).toBe('CA123456789AR');
    expect(within(shipment).getByRole('button', { name: messages.copied })).toBeInTheDocument();
  });

  it('given an item whose product has a photo, when the customer opens the order, then the item shows its thumbnail', async () => {
    const photo = { placeholder: 'data:image/webp;base64,UklGRg==', files: [{ width: 400, url: '/uploads/luna-rosa-400.webp' }] };
    const [item] = detail().items;
    stubOrders(
      () => answer(200, { orders: [], hasMore: false }),
      () => answer(200, detail({ items: [{ ...item, image: photo }] })),
    );
    renderRoute(`/cuenta/pedidos/${SECOND}`);

    const items = await screen.findByRole('region', { name: messages.items });
    expect(within(items).getByRole('img', { name: es.cart.photoAlt('Pijama Luna', 'Rosa') })).toHaveAttribute(
      'src',
      '/uploads/luna-rosa-400.webp',
    );
  });

  it('given an item whose product has no photo, when the customer opens the order, then the item shows the placeholder', async () => {
    stubOrders(
      () => answer(200, { orders: [], hasMore: false }),
      () => answer(200, detail()),
    );
    renderRoute(`/cuenta/pedidos/${SECOND}`);

    const name = await screen.findByText('Pijama Luna');
    expect(name.closest('li')?.querySelector('.ph.tracking-item-photo')).not.toBeNull();
  });

  it('given a paid order not yet shipped, when the customer opens it, then it says the tracking number is to come', async () => {
    stubOrders(
      () => answer(200, { orders: [], hasMore: false }),
      () => answer(200, detail({ status: 'PAID', shipmentTrackingNumber: null })),
    );
    renderRoute(`/cuenta/pedidos/${SECOND}`);

    expect(await screen.findByText(messages.noShipmentYet)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: messages.trackAtCorreo })).not.toBeInTheDocument();
  });

  it('given a shipped order and a delivered one, when "Mis pedidos" opens, then the first card shows 3 of 4 steps and "Seguir envío", the second 4 of 4 and "Entregado" (T-E7-64)', async () => {
    stubOrders(() =>
      answer(200, {
        orders: [
          summary(SECOND, 1002, '2026-10-14T15:00:00Z', 'SHIPPED', 100),
          summary(FIRST, 1001, '2026-10-13T15:00:00Z', 'DELIVERED', 100),
        ],
        hasMore: false,
      }),
    );
    renderRoute('/cuenta/pedidos');

    const [shipped, delivered] = await within(screen.getByRole('main')).findAllByRole('article');
    if (shipped === undefined || delivered === undefined) {
      throw new Error('Two cards expected');
    }
    const shippedSteps = within(within(shipped).getByRole('list', { name: es.tracking.paid.progress })).getAllByRole(
      'listitem',
    );
    expect(shippedSteps.filter((step) => step.classList.contains('is-reached'))).toHaveLength(3);
    expect(shippedSteps[2]).toHaveAttribute('aria-current', 'step');
    expect(shipped).toHaveTextContent(messages.step(3));
    expect(within(shipped).getByRole('link', { name: `${messages.trackShipment} · Pedido #1002` })).toHaveAttribute(
      'href',
      `/cuenta/pedidos/${SECOND}`,
    );
    const deliveredSteps = within(delivered).getAllByRole('listitem');
    expect(deliveredSteps.filter((step) => step.classList.contains('is-reached'))).toHaveLength(4);
    expect(delivered.querySelector('.order-progress-caption')).toHaveTextContent(es.tracking.statuses.DELIVERED);
    expect(within(delivered).queryByRole('link', { name: new RegExp(messages.trackShipment) })).not.toBeInTheDocument();
  });

  it('given an order awaiting payment, when "Mis pedidos" opens, then its card has no bar and says it is paid from the email (T-E7-64)', async () => {
    stubOrders(() =>
      answer(200, { orders: [summary(FIRST, 1001, '2026-10-13T15:00:00Z', 'AWAITING_PAYMENT', 100)], hasMore: false }),
    );
    renderRoute('/cuenta/pedidos');

    const [card] = await within(screen.getByRole('main')).findAllByRole('article');
    if (card === undefined) {
      throw new Error('A card expected');
    }
    expect(within(card).queryByRole('list')).not.toBeInTheDocument();
    expect(card).toHaveTextContent(messages.payFromEmail);
  });

  it('given an order paid with MercadoPago, when its detail opens, then it says "Pagado con Mercado Pago" (T-E7-64, T-E4-19)', async () => {
    stubOrders(
      () => answer(200, { orders: [], hasMore: false }),
      () => answer(200, detail({ status: 'DELIVERED' })),
    );
    renderRoute(`/cuenta/pedidos/${SECOND}`);

    const items = await screen.findByRole('region', { name: messages.items });
    expect(within(items).getByText('Pagado con Mercado Pago')).toBeInTheDocument();
  });

  it('given an order awaiting payment, when its detail opens, then it names no one who collected it (T-E4-19)', async () => {
    stubOrders(
      () => answer(200, { orders: [], hasMore: false }),
      () => answer(200, detail({ status: 'AWAITING_PAYMENT', shipmentTrackingNumber: null })),
    );
    renderRoute(`/cuenta/pedidos/${SECOND}`);

    await screen.findByRole('region', { name: messages.items });
    expect(screen.queryByText(messages.paidWith.MERCADOPAGO)).not.toBeInTheDocument();
  });

  it('given another person\'s order, when the customer tries to open it, then it is told the order is not found', async () => {
    stubOrders(() => answer(200, { orders: [], hasMore: false }), () => answer(404, { problem: 'ORDER_NOT_FOUND' }));
    renderRoute(`/cuenta/pedidos/${FIRST}`);

    expect(await screen.findByRole('heading', { name: messages.notFoundTitle })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.back })).toHaveAttribute('href', '/cuenta/pedidos');
  });
});
