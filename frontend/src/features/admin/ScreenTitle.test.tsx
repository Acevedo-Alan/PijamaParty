import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = esAdmin.screenHelp;

/** The 13 screens of the menu (AdminNav.tsx). */
const SCREENS = [
  '/admin',
  '/admin/pedidos',
  '/admin/arrepentimientos',
  '/admin/clientes',
  '/admin/productos',
  '/admin/categorias',
  '/admin/stock',
  '/admin/cupones',
  '/admin/resenas',
  '/admin/portada',
  '/admin/envios',
  '/admin/datos-de-la-tienda',
  '/admin/actividad',
];

/** Signed in; every other read fails, as the title and its "?" are all these tests look at. */
function stub() {
  return stubApi({ 'GET /api/admin/auth/me': signedIn, 'GET /api/admin/orders': answer(500) });
}

async function openOrders() {
  stub();
  renderRoute('/admin/pedidos');
  const help = await screen.findByRole('button', { name: messages.open(esAdmin.orders) });
  await userEvent.click(help);
  return help;
}

describe('screen help with the chibi (UC-47)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the Pedidos screen, when "?" is pressed, then a card with the chibi and its lines opens, and "Entendido" closes it giving the focus back', async () => {
    const help = await openOrders();

    expect(help).toHaveAttribute('aria-expanded', 'true');
    const card = screen.getByRole('region', { name: esAdmin.orders });
    expect(help).toHaveAttribute('aria-controls', card.id);
    for (const line of messages.screens.orders) {
      expect(within(card).getByText(line)).toBeInTheDocument();
    }
    await waitFor(() => {
      expect(card.querySelector('svg.chibi')).toHaveAttribute('data-pose', 'guide');
    });

    await userEvent.click(within(card).getByRole('button', { name: messages.close }));

    expect(help).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('region', { name: esAdmin.orders })).toBeNull();
    expect(help).toHaveFocus();
  });

  it('given the card open, when Escape is pressed, then it closes and the focus goes back to "?"', async () => {
    const help = await openOrders();

    await userEvent.keyboard('{Escape}');

    expect(help).toHaveAttribute('aria-expanded', 'false');
    expect(help).toHaveFocus();
  });

  it('given the card open, when the page is pressed outside it, then it closes', async () => {
    const help = await openOrders();

    await userEvent.click(screen.getByRole('heading', { level: 1 }));

    expect(help).toHaveAttribute('aria-expanded', 'false');
  });

  it.each(SCREENS)('given %s, when it opens, then its title is followed by its own "?" and keeps its name', async (path) => {
    stub();
    renderRoute(path);

    const title = await screen.findByRole('heading', { level: 1 });
    const name = title.textContent;
    expect(name).not.toBe('');
    const help = screen.getByRole('button', { name: messages.open(name) });
    expect(title.nextElementSibling?.contains(help)).toBe(true);
  });
});
