import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = esAdmin.customerProfile;
const PROFILE = 'GET /api/admin/customers/{id}';
const CUSTOMERS = 'GET /api/admin/customers';

function order(id: string, number: number, status: string, totalCents: number, createdAt: string) {
  return { id, number, createdAt, status, totalCents };
}

function profile(overrides: Record<string, unknown> = {}) {
  return {
    id: 'u-1',
    name: 'Ana Gómez',
    email: 'ana@example.com',
    emailVerified: true,
    phone: '3425551234',
    registeredAt: '2026-10-05T15:00:00Z',
    providers: ['GOOGLE'],
    addresses: [
      {
        id: 'a-1',
        street: 'San Martín',
        number: '2550',
        floor: '3',
        apartment: 'B',
        city: 'Santa Fe',
        province: 'SANTA_FE',
        postalCode: 'S3000ABC',
        primary: true,
      },
      {
        id: 'a-2',
        street: 'Rivadavia',
        number: '100',
        floor: null,
        apartment: null,
        city: 'Rosario',
        province: 'SANTA_FE',
        postalCode: 'S2000ABC',
        primary: false,
      },
    ],
    orders: [
      order('o-3', 1003, 'AWAITING_PAYMENT', 500_000, '2026-10-12T15:00:00Z'),
      order('o-2', 1002, 'CANCELLED', 1_000_000, '2026-10-11T15:00:00Z'),
      order('o-1', 1001, 'DELIVERED', 3_000_000, '2026-10-10T15:00:00Z'),
    ],
    hasMoreOrders: false,
    orderCount: 3,
    purchases: 1,
    spentCents: 3_000_000,
    lastPurchaseAt: '2026-10-10T15:00:00Z',
    ...overrides,
  };
}

describe('customer profile (UC-34, T-E8-11)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a customer with three orders, when the profile opens, then it shows 3 orders, 1 purchase and $30.000 spent', async () => {
    const api = stubApi({ 'GET /api/admin/auth/me': signedIn, [PROFILE]: answer(200, profile()) });
    renderRoute('/admin/clientes/u-1');

    expect(await screen.findByRole('heading', { level: 1, name: 'Ana Gómez' })).toBeInTheDocument();
    expect(screen.getByText(messages.since(formatDate(new Date('2026-10-05T15:00:00Z'))))).toBeInTheDocument();
    const figures = within(screen.getByRole('region', { name: messages.figures }));
    expect(figures.getByText(messages.purchases).nextElementSibling).toHaveTextContent('1');
    expect(figures.getByText(messages.ofOrders(3))).toBeInTheDocument();
    expect(figures.getByText(messages.spent).nextElementSibling?.textContent).toBe(formatMoney(3_000_000));
    expect(figures.getByText(messages.lastPurchase).nextElementSibling).toHaveTextContent(
      formatDate(new Date('2026-10-10T15:00:00Z')),
    );

    const orders = within(screen.getByRole('region', { name: messages.orders }));
    const links = orders.getAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual(['#1003', '#1002', '#1001']);
    expect(links[2]).toHaveAttribute('href', '/admin/pedidos/o-1');
    const delivered = within(links[2]?.closest('li') as HTMLElement);
    expect(delivered.getByText(es.tracking.statuses.DELIVERED)).toBeInTheDocument();
    expect(links[2]?.closest('li')?.textContent).toContain(formatMoney(3_000_000));
    expect(api.callsTo(PROFILE).map((call) => call.options.params)).toEqual([
      { path: { id: 'u-1' }, query: { ordersPage: 0 } },
    ]);
  });

  it('given a customer, when the profile opens, then the contact links to mail and WhatsApp and the addresses list the main one first', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [PROFILE]: answer(200, profile()) });
    renderRoute('/admin/clientes/u-1');

    const contact = within(await screen.findByRole('region', { name: messages.contact }));
    expect(contact.getByText(messages.verified)).toBeInTheDocument();
    expect(contact.getByRole('link', { name: messages.writeEmail })).toHaveAttribute('href', 'mailto:ana@example.com');
    const whatsApp = contact.getByRole('link', { name: messages.openWhatsApp });
    expect(whatsApp).toHaveAttribute('href', 'https://wa.me/5493425551234');
    expect(whatsApp).toHaveAttribute('rel', 'noopener noreferrer');
    expect(contact.getByText(messages.providers.GOOGLE)).toBeInTheDocument();

    const addresses = within(screen.getByRole('region', { name: messages.addresses })).getAllByRole('listitem');
    expect(addresses[0]).toHaveTextContent(messages.primary);
    expect(addresses[0]).toHaveTextContent('San Martín 2550, Piso 3, depto. B');
    expect(addresses[0]).toHaveTextContent(messages.cityLine('Santa Fe', es.provinces.SANTA_FE, 'S3000ABC'));
    expect(addresses[1]).toHaveTextContent('Rivadavia 100');
    expect(addresses[1]).not.toHaveTextContent(messages.primary);
  });

  it('given the copy button of the email, when pressed, then the email is on the clipboard and the button says so', async () => {
    const user = userEvent.setup();
    stubApi({ 'GET /api/admin/auth/me': signedIn, [PROFILE]: answer(200, profile()) });
    renderRoute('/admin/clientes/u-1');

    const contact = within(await screen.findByRole('region', { name: messages.contact }));
    const copyEmail = contact.getByRole('button', { name: messages.copyEmail });
    await user.click(copyEmail);

    expect(await navigator.clipboard.readText()).toBe('ana@example.com');
    // The status sits next to its button, outside it, so screen readers announce it.
    expect(copyEmail.nextElementSibling).toHaveAttribute('role', 'status');
    expect(copyEmail.nextElementSibling).toHaveTextContent(messages.copied);
    expect(contact.getByRole('button', { name: messages.copyPhone }).nextElementSibling).toBeEmptyDOMElement();
    // After a moment the button offers "Copiar" again, so a second copy is announced too.
    await vi.waitFor(() => {
      expect(copyEmail.nextElementSibling).toBeEmptyDOMElement();
    }, 3000);
    expect(copyEmail).toHaveTextContent(messages.copy);
  });

  it('given a customer with nothing saved, when the profile opens, then each block says so and no WhatsApp shows', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [PROFILE]: answer(
        200,
        profile({
          emailVerified: false,
          phone: null,
          providers: [],
          addresses: [],
          orders: [],
          orderCount: 0,
          purchases: 0,
          spentCents: 0,
          lastPurchaseAt: null,
        }),
      ),
    });
    renderRoute('/admin/clientes/u-1');

    const contact = within(await screen.findByRole('region', { name: messages.contact }));
    expect(contact.getByText(messages.unverified)).toBeInTheDocument();
    expect(contact.getByText(messages.noPhone)).toBeInTheDocument();
    expect(contact.queryByRole('link', { name: messages.openWhatsApp })).not.toBeInTheDocument();
    expect(contact.getByText(messages.noProviders)).toBeInTheDocument();
    expect(screen.getByText(messages.noAddresses)).toBeInTheDocument();
    expect(screen.getByText(messages.noOrders)).toBeInTheDocument();
    expect(screen.getByText(messages.noPurchases)).toBeInTheDocument();
  });

  it('given more than 20 orders, when "Ver más" is pressed, then the next page of orders is added', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [PROFILE]: [
        answer(200, profile({ hasMoreOrders: true })),
        answer(200, profile({ orders: [order('o-0', 1000, 'PAID', 100, '2026-10-01T15:00:00Z')] })),
      ],
    });
    renderRoute('/admin/clientes/u-1');

    await userEvent.click(await screen.findByRole('button', { name: esAdmin.showMore }));

    expect(await screen.findByRole('link', { name: '#1000' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /^#/ })).toHaveLength(4);
    expect(screen.queryByRole('button', { name: esAdmin.showMore })).not.toBeInTheDocument();
    expect(api.callsTo(PROFILE).map((call) => call.options.params?.query)).toEqual([
      { ordersPage: 0 },
      { ordersPage: 1 },
    ]);
  });

  it('given a deleted customer, when the profile opens, then it says the account no longer exists', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [PROFILE]: answer(404, { problem: 'CUSTOMER_NOT_FOUND' }) });
    renderRoute('/admin/clientes/u-1');

    expect(await screen.findByText(messages.notFound)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.backToList })).toHaveAttribute('href', '/admin/clientes');
  });

  it('given a filtered customer list, when a customer is opened and the back link pressed, then the list keeps its filters', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [CUSTOMERS]: answer(200, {
        items: [
          { id: 'u-1', name: 'Ana Gómez', email: 'ana@example.com', registeredAt: '2026-10-05T15:00:00Z', orders: 3 },
        ],
        hasMore: false,
      }),
      [PROFILE]: answer(200, profile()),
    });
    const { router } = renderRoute('/admin/clientes?buscar=gomez');

    await userEvent.click(await screen.findByRole('link', { name: 'Ana Gómez' }));
    await userEvent.click(await screen.findByRole('link', { name: messages.backToList }));

    expect(router.state.location.pathname).toBe('/admin/clientes');
    expect(router.state.location.search).toBe('?buscar=gomez');
  });

  it('given an order opened from the profile, when its back link is pressed, then it returns to the profile, which still returns to the filtered list', async () => {
    const id = '0f8fad5b-d9cb-469f-a165-70867728950e';
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [CUSTOMERS]: answer(200, {
        items: [{ id, name: 'Ana Gómez', email: 'ana@example.com', registeredAt: '2026-10-05T15:00:00Z', orders: 3 }],
        hasMore: false,
      }),
      [PROFILE]: answer(200, profile({ id })),
      'GET /api/admin/orders/{id}': answer(404, { problem: 'ORDER_NOT_FOUND' }),
    });
    const { router } = renderRoute('/admin/clientes?buscar=gomez');

    await userEvent.click(await screen.findByRole('link', { name: 'Ana Gómez' }));
    await userEvent.click(await screen.findByRole('link', { name: '#1001' }));
    expect(router.state.location.pathname).toBe('/admin/pedidos/o-1');
    await userEvent.click(await screen.findByRole('link', { name: esAdmin.orderDetail.backToCustomer }));

    expect(router.state.location.pathname).toBe(`/admin/clientes/${id}`);
    await userEvent.click(await screen.findByRole('link', { name: messages.backToList }));
    expect(router.state.location.pathname).toBe('/admin/clientes');
    expect(router.state.location.search).toBe('?buscar=gomez');
  });
});
