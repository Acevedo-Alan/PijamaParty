import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { components } from '../../api/schema.d.ts';
import { es } from '../../i18n/es.ts';
import { problem, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

type OrderBody = components['schemas']['AdminOrderResponse'];

const messages = es.admin.orderDetail;
const DETAIL = 'GET /api/admin/orders/{id}';
const CHANGE = 'POST /api/admin/orders/{id}/status';

function order(overrides: Partial<OrderBody> = {}): OrderBody {
  return {
    id: 'o-1',
    number: 1001,
    status: 'PAID',
    nextStatuses: ['PREPARING', 'CANCELLED'],
    createdAt: '2026-10-13T18:00:00Z',
    updatedAt: '2026-10-13T18:05:00Z',
    deliveredAt: null,
    buyer: { firstName: 'Ana', lastName: 'Pérez', email: 'ana@example.com', phone: '3425551234', nationalId: '12345678' },
    address: {
      street: 'San Martín',
      number: '1234',
      floor: '3',
      apartment: 'B',
      city: 'Santa Fe',
      province: 'SANTA_FE',
      postalCode: 'S3000ABC',
    },
    lines: [
      {
        productName: 'Pijama Luna',
        size: 'M',
        color: 'Rosa',
        quantity: 2,
        unitPriceCents: 2_000_000,
        amountCents: 4_000_000,
      },
    ],
    subtotalCents: 4_000_000,
    discountCents: 0,
    couponCode: null,
    shippingCents: 450_000,
    totalCents: 4_450_000,
    paymentProvider: 'MERCADOPAGO',
    shipmentTrackingNumber: null,
    alerts: [],
    ...overrides,
  };
}

async function openOrder() {
  renderRoute('/admin/pedidos/o-1');
  await screen.findByRole('heading', { level: 1, name: messages.title(1001) });
}

function statusSection() {
  return within(screen.getByRole('region', { name: messages.status }));
}

describe('order detail (UC-18)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given an order, when opened, then it shows the buyer, the address, the lines and the amounts', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [DETAIL]: answer(200, order()) });

    await openOrder();

    expect(screen.getByText(messages.nationalId('12345678'))).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '3425551234' })).toHaveAttribute('href', 'tel:3425551234');
    expect(screen.getByText(/San Martín 1234, Piso 3, depto\. B/)).toBeInTheDocument();
    expect(screen.getByText('Pijama Luna')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(statusSection().getByRole('button', { name: messages.actions.PREPARING })).toBeInTheDocument();
    expect(statusSection().getByRole('button', { name: messages.actions.CANCELLED })).toBeInTheDocument();
  });

  it('given an order whose buyer did not get the email, when opened, then an alert shows the buyers phone', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [DETAIL]: answer(200, order({ alerts: ['BUYER_EMAIL_FAILED'] })),
    });

    await openOrder();

    expect(screen.getByRole('alert')).toHaveTextContent(messages.alerts.BUYER_EMAIL_FAILED('3425551234'));
  });

  it('given an order being withdrawn out of term, when opened, then an alert shows its code and the mark', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [DETAIL]: answer(
        200,
        order({
          withdrawal: { id: 'w-1', code: 'ARR-7K2M9QXZ', outOfTerm: true, requestedAt: '2026-10-14T12:00:00Z' },
          alerts: ['WITHDRAWAL_REQUESTED'],
        }),
      ),
    });

    await openOrder();

    expect(screen.getByRole('alert')).toHaveTextContent(messages.alerts.WITHDRAWAL_REQUESTED('ARR-7K2M9QXZ', true));
    expect(screen.getByRole('alert')).toHaveTextContent(/fuera de término/);
  });

  it('given an order whose buyers email bounced, when opened, then an alert shows the buyers phone', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [DETAIL]: answer(200, order({ alerts: ['BUYER_EMAIL_BOUNCED'] })),
    });

    await openOrder();

    expect(screen.getByRole('alert')).toHaveTextContent(messages.alerts.BUYER_EMAIL_BOUNCED('3425551234'));
  });

  it('given an order paid after it expired, when opened, then it alerts to refund it and offers no change', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [DETAIL]: answer(200, order({ status: 'EXPIRED', nextStatuses: [], alerts: ['PAID_AFTER_CLOSING'] })),
    });

    await openOrder();

    expect(screen.getByRole('alert')).toHaveTextContent(messages.alerts.PAID_AFTER_CLOSING);
    expect(statusSection().queryByRole('button')).not.toBeInTheDocument();
  });

  it('given a preparing order, when shipped with its tracking number, then the change is sent and shown', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [DETAIL]: [
        answer(200, order({ status: 'PREPARING', nextStatuses: ['SHIPPED', 'CANCELLED'] })),
        answer(200, order({ status: 'SHIPPED', nextStatuses: ['DELIVERED'], shipmentTrackingNumber: 'CA123' })),
      ],
      [CHANGE]: answer(200, { status: 'SHIPPED' }),
    });
    await openOrder();

    await userEvent.click(statusSection().getByRole('button', { name: messages.actions.SHIPPED }));
    await userEvent.click(statusSection().getByRole('button', { name: messages.ship }));
    expect(statusSection().getByRole('alert')).toHaveTextContent(messages.problems.MISSING_TRACKING_NUMBER);
    await userEvent.type(statusSection().getByLabelText(messages.shipmentLabel), 'CA123');
    await userEvent.click(statusSection().getByRole('button', { name: messages.ship }));

    expect(await statusSection().findByRole('status')).toHaveTextContent(
      messages.changed(es.tracking.statuses.SHIPPED),
    );
    expect(api.callsTo(CHANGE)[0]?.options.body).toEqual({
      from: 'PREPARING',
      to: 'SHIPPED',
      shipmentTrackingNumber: 'CA123',
    });
    expect(await statusSection().findByLabelText(messages.shipment)).toHaveValue('CA123');
    expect(statusSection().getByRole('button', { name: messages.actions.DELIVERED })).toBeInTheDocument();
  });

  it('given a paid order, when cancelled, then the administrator decides whether the stock goes back', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [DETAIL]: [answer(200, order()), answer(200, order({ status: 'CANCELLED', nextStatuses: [] }))],
      [CHANGE]: answer(200, { status: 'CANCELLED' }),
    });
    await openOrder();

    await userEvent.click(statusSection().getByRole('button', { name: messages.actions.CANCELLED }));
    await userEvent.click(statusSection().getByRole('button', { name: messages.confirmCancel }));
    expect(statusSection().getByRole('alert')).toHaveTextContent(messages.problems.RESTOCK_DECISION_REQUIRED);
    expect(api.callsTo(CHANGE)).toHaveLength(0);
    await userEvent.click(statusSection().getByRole('radio', { name: messages.restockYes }));
    await userEvent.click(statusSection().getByRole('button', { name: messages.confirmCancel }));

    await vi.waitFor(() => {
      expect(api.callsTo(CHANGE)[0]?.options.body).toEqual({ from: 'PAID', to: 'CANCELLED', restock: true });
    });
    expect(await screen.findByText(es.tracking.statuses.CANCELLED, { selector: '.admin-badge' })).toBeInTheDocument();
  });

  it('given an order that changed meanwhile, when changed from the status seen, then it says so and reloads', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [DETAIL]: [answer(200, order()), answer(200, order({ status: 'CANCELLED', nextStatuses: [] }))],
      [CHANGE]: problem(409, { problem: 'ORDER_STATUS_CHANGED', currentStatus: 'CANCELLED' }),
    });
    await openOrder();

    await userEvent.click(statusSection().getByRole('button', { name: messages.actions.PREPARING }));

    expect(await statusSection().findByRole('alert')).toHaveTextContent(messages.stale);
    expect(await screen.findByText(es.tracking.statuses.CANCELLED, { selector: '.admin-badge' })).toBeInTheDocument();
    expect(api.callsTo(DETAIL)).toHaveLength(2);
  });

  it('given a shipped order, when its tracking number is corrected, then the new one is sent', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [DETAIL]: answer(
        200,
        order({ status: 'SHIPPED', nextStatuses: ['DELIVERED'], shipmentTrackingNumber: 'CA000' }),
      ),
      'PUT /api/admin/orders/{id}/shipment-tracking-number': answer(200, { shipmentTrackingNumber: 'CA123' }),
    });
    await openOrder();

    const field = statusSection().getByLabelText(messages.shipment);
    await userEvent.clear(field);
    await userEvent.type(field, 'CA123');
    await userEvent.click(statusSection().getByRole('button', { name: messages.correctShipment }));

    expect(await statusSection().findByRole('status')).toHaveTextContent(messages.correctedShipment);
    expect(api.callsTo('PUT /api/admin/orders/{id}/shipment-tracking-number')[0]?.options.body).toEqual({
      shipmentTrackingNumber: 'CA123',
    });
  });

  it('given no such order, when opened, then it says so', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [DETAIL]: problem(404, { problem: 'ORDER_NOT_FOUND' }) });
    renderRoute('/admin/pedidos/o-1');

    expect(await screen.findByText(messages.notFound)).toBeInTheDocument();
  });
});
