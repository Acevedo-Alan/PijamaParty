import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = esAdmin.search;
const ORDERS = 'GET /api/admin/orders';
const PRODUCTS = 'GET /api/admin/catalog/products';
const CUSTOMERS = 'GET /api/admin/customers';
const none = answer(200, { items: [], hasMore: false });

function stub(routes: Parameters<typeof stubApi>[0] = {}) {
  return stubApi({
    'GET /api/admin/auth/me': signedIn,
    'GET /api/admin/dashboard': answer(200, {}),
    'GET /api/admin/dashboard/counts': answer(200, {}),
    'GET /api/admin/activity': none,
    [ORDERS]: none,
    [PRODUCTS]: none,
    [CUSTOMERS]: none,
    ...routes,
  });
}

/** The back office on a page that asks nothing else (the counts of the menu are not needed). */
async function openBackOffice() {
  renderRoute('/admin/actividad');
  return screen.findByRole('button', { name: new RegExp(messages.open) });
}

describe('the global search of the top bar (UC-39)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('given the back office, when Ctrl+K is pressed, then the search opens with the focus in its box and Escape closes it', async () => {
    stub();
    const button = await openBackOffice();

    await userEvent.keyboard('{Control>}k{/Control}');

    const dialog = await screen.findByRole('dialog', { name: messages.title });
    expect(within(dialog).getByRole('searchbox', { name: messages.label })).toHaveFocus();
    // jsdom has no Escape handling for dialogs: the browser sends a cancelable "cancel" event.
    fireEvent(dialog, new Event('cancel', { cancelable: true }));
    expect(screen.queryByRole('dialog', { name: messages.title })).not.toBeInTheDocument();
    expect(button).toBeInTheDocument();
  });

  it('given the search button, when it is pressed and the search closes, then the focus returns to the button', async () => {
    stub();
    const button = await openBackOffice();

    await userEvent.click(button);
    const dialog = await screen.findByRole('dialog', { name: messages.title });
    fireEvent(dialog, new Event('cancel', { cancelable: true }));

    expect(screen.queryByRole('dialog', { name: messages.title })).not.toBeInTheDocument();
    expect(button).toHaveFocus();
  });

  it('given a text of two letters, when typed, then the three lists are asked and each match links to its page', async () => {
    const api = stub({
      [ORDERS]: answer(200, {
        items: [{ id: 'o-1', number: 1001, createdAt: '2026-10-15T15:00:00Z', status: 'PAID', buyerName: 'Ana Pérez',
          city: 'Santa Fe', province: 'SANTA_FE', totalCents: 2_300_000 }],
        hasMore: false,
      }),
      [PRODUCTS]: answer(200, {
        items: [{ id: 'p-1', categoryId: 'c-1', name: 'Pijama Luna', published: false, variantCount: 2 }],
        hasMore: false,
      }),
      [CUSTOMERS]: answer(200, {
        items: [{ id: 'u-1', name: 'Ana Gómez', email: 'ana@example.com', registeredAt: '2026-10-01T10:00:00Z', orders: 1 }],
        hasMore: false,
      }),
    });
    await openBackOffice();
    await userEvent.keyboard('{Control>}k{/Control}');
    const box = await screen.findByRole('searchbox', { name: messages.label });

    await userEvent.type(box, 'an');

    const dialog = screen.getByRole('dialog', { name: messages.title });
    expect(await within(dialog).findByRole('link', { name: '#1001 · Ana Pérez' })).toHaveAttribute('href', '/admin/pedidos/o-1');
    expect(within(dialog).getByRole('link', { name: `Pijama Luna (${messages.hidden})` })).toHaveAttribute('href', '/admin/productos/p-1');
    expect(within(dialog).getByRole('link', { name: 'Ana Gómez · ana@example.com' })).toHaveAttribute('href', '/admin/clientes/u-1');
    expect(api.callsTo(ORDERS).at(-1)?.options.params?.query).toMatchObject({ q: 'an', page: 0 });
    // Only the hint of the back-office session: nothing of what was typed (R-05)
    expect(Object.keys(localStorage)).toEqual(['pp-admin']);
  });

  it('given a single letter, when typed, then nothing is asked', async () => {
    const api = stub();
    await openBackOffice();
    await userEvent.keyboard('{Control>}k{/Control}');

    await userEvent.type(await screen.findByRole('searchbox', { name: messages.label }), 'a');

    expect(screen.getByText(messages.tooShort)).toBeInTheDocument();
    expect(api.callsTo(ORDERS)).toHaveLength(0);
    expect(api.callsTo(CUSTOMERS)).toHaveLength(0);
  });

  it('given the customers failing, when searching, then orders and products still show', async () => {
    stub({
      [ORDERS]: answer(200, {
        items: [{ id: 'o-1', number: 1001, createdAt: '2026-10-15T15:00:00Z', status: 'PAID', buyerName: 'Ana Pérez',
          city: 'Santa Fe', province: 'SANTA_FE', totalCents: 2_300_000 }],
        hasMore: false,
      }),
      [CUSTOMERS]: new Error('down'),
    });
    await openBackOffice();
    await userEvent.keyboard('{Control>}k{/Control}');

    await userEvent.type(await screen.findByRole('searchbox', { name: messages.label }), 'ana');

    expect(await screen.findByRole('link', { name: '#1001 · Ana Pérez' })).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent(messages.failed);
    expect(screen.getByText(messages.none)).toBeInTheDocument();
  });
});
