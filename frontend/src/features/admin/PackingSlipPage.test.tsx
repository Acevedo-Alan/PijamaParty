import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { components } from '../../api/schema.d.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

type OrderBody = components['schemas']['AdminOrderResponse'];

const messages = esAdmin.packingSlip;
const detail = esAdmin.orderDetail;
const DETAIL = 'GET /api/admin/orders/{id}';

function order(overrides: Partial<OrderBody> = {}): OrderBody {
  return {
    id: 'o-1',
    number: 1001,
    status: 'PREPARING',
    nextStatuses: ['SHIPPED', 'CANCELLED'],
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
        sku: 'PL-M-ROSA',
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

describe('packing slip (UC-18, T-E8-07)', () => {
  let print: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    print = vi.fn();
    vi.stubGlobal('print', print);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('given a PREPARING order, when the slip opens, then it shows the lines, name, phone and address, no amount, and opens the print dialog once', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [DETAIL]: answer(200, order()) });

    renderRoute('/admin/pedidos/o-1/remito');

    const slip = within(await screen.findByRole('article', { name: detail.title(1001) }));
    expect(slip.getByText('Pijama Party Santa Fe')).toBeInTheDocument();
    expect(slip.getByText('Ana Pérez')).toBeInTheDocument();
    expect(slip.getByText('3425551234')).toBeInTheDocument();
    expect(slip.getByText('San Martín 1234, Piso 3, depto. B')).toBeInTheDocument();
    expect(slip.getByText('S3000ABC Santa Fe, Santa Fe')).toBeInTheDocument();
    const line = slip.getAllByRole('row')[1] as HTMLElement;
    expect(within(line).getAllByRole('cell').map((cell) => cell.textContent)).toEqual([
      'Pijama Luna',
      'M',
      'Rosa',
      '2',
      'PL-M-ROSA',
    ]);
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.backToOrder })).toHaveAttribute('href', '/admin/pedidos/o-1');
    expect(print).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole('button', { name: messages.printAgain }));
    expect(print).toHaveBeenCalledTimes(2);
  });

  it('given a shipped order, when the slip opens, then it carries the tracking number', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [DETAIL]: answer(200, order({ status: 'SHIPPED', shipmentTrackingNumber: 'AR000111222' })),
    });

    renderRoute('/admin/pedidos/o-1/remito');

    const slip = within(await screen.findByRole('article', { name: detail.title(1001) }));
    expect(slip.getByText(`${messages.shipment}: AR000111222`)).toBeInTheDocument();
  });

  it('given an order awaiting payment, when its slip is opened, then there is no slip and nothing is printed', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [DETAIL]: answer(200, order({ status: 'AWAITING_PAYMENT' })) });

    renderRoute('/admin/pedidos/o-1/remito');

    expect(await screen.findByText(messages.notPrintable)).toBeInTheDocument();
    expect(screen.queryByRole('article')).not.toBeInTheDocument();
    expect(print).not.toHaveBeenCalled();
  });

  it.each([
    ['PAID', true],
    ['PREPARING', true],
    ['SHIPPED', true],
    ['DELIVERED', true],
    ['AWAITING_PAYMENT', false],
    ['CANCELLED', false],
    ['EXPIRED', false],
  ] as const)('given a %s order, when its detail opens, then "Imprimir remito" shows: %s', async (status, shown) => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [DETAIL]: answer(200, order({ status, nextStatuses: [] })) });

    renderRoute('/admin/pedidos/o-1');

    await screen.findByRole('heading', { level: 1, name: detail.title(1001) });
    const link = screen.queryByRole('link', { name: messages.print });
    if (shown) {
      expect(link).toHaveAttribute('href', '/admin/pedidos/o-1/remito');
    } else {
      expect(link).not.toBeInTheDocument();
    }
  });
});
