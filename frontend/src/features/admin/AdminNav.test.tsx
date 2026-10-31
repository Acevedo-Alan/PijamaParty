import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const COUNTS = 'GET /api/admin/dashboard/counts';
const ACTIVITY = 'GET /api/admin/activity';

function counts(paidOrders: number) {
  return answer(200, { paidOrders });
}

/**
 * The side menu (the panel's copy only exists while it is open). jsdom does not evaluate the 860 px media query, so
 * the side column keeps its phone style, display: none; the queries reach into it as hidden.
 */
async function sideMenu() {
  await screen.findByRole('button', { name: esAdmin.menu });
  const nav = document.querySelector<HTMLElement>('.admin-side nav');
  if (nav === null) {
    throw new Error('No side menu');
  }
  const menu = within(nav);
  return {
    getAllByRole: (role: string) => menu.getAllByRole(role, { hidden: true }),
    getByRole: (role: string, options: { name: string | RegExp }) => menu.getByRole(role, { ...options, hidden: true }),
    findByRole: (role: string, options: { name: string | RegExp }) =>
      menu.findByRole(role, { ...options, hidden: true }),
  };
}

describe('back-office navigation (E8a, T-E8-05)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the back office, when the menu shows, then it has every group in order, then "Ver tienda" and "Cerrar sesión"', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: counts(0), [ACTIVITY]: answer(200, { items: [], hasMore: false }) });
    renderRoute('/admin/actividad');

    const menu = await sideMenu();
    // Each group's list is named by its title; "Envíos" and the end stand alone.
    expect(menu.getAllByRole('list').map((list) => list.getAttribute('aria-labelledby') === null
      ? null
      : document.getElementById(list.getAttribute('aria-labelledby') ?? '')?.textContent)).toEqual([
      esAdmin.navGroups.sales,
      esAdmin.navGroups.catalog,
      esAdmin.navGroups.marketing,
      null,
      esAdmin.navGroups.settings,
      null,
    ]);
    expect(menu.getAllByRole('link').map((link) => link.textContent)).toEqual([
      esAdmin.orders,
      esAdmin.withdrawals,
      esAdmin.products,
      esAdmin.categories,
      esAdmin.coupons,
      esAdmin.reviews,
      esAdmin.home,
      esAdmin.shipping,
      esAdmin.activity,
      esAdmin.viewStore,
    ]);
    expect(menu.getByRole('button', { name: esAdmin.signOut })).toBeInTheDocument();
  });

  it('given /admin/actividad, when the menu shows, then "Actividad" is marked as the current page', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: counts(0), [ACTIVITY]: answer(200, { items: [], hasMore: false }) });
    renderRoute('/admin/actividad');

    const menu = await sideMenu();
    expect(menu.getByRole('link', { name: esAdmin.activity })).toHaveAttribute('aria-current', 'page');
    expect(menu.getByRole('link', { name: esAdmin.orders })).not.toHaveAttribute('aria-current');
  });

  it('given 2 PAID orders, when the back office opens, then "Pedidos" shows 2', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: counts(2), [ACTIVITY]: answer(200, { items: [], hasMore: false }) });
    renderRoute('/admin/actividad');

    const menu = await sideMenu();
    const orders = await menu.findByRole('link', { name: new RegExp(`^${esAdmin.orders}`) });
    expect(orders).toHaveTextContent(`${esAdmin.orders}2`);
    expect(orders).toHaveAccessibleName(`${esAdmin.orders} ${esAdmin.paidWaiting(2)}`);
  });

  it('given a new paid order, when the order list loads, then "Pedidos" reads the count again', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [COUNTS]: [counts(1), counts(3)],
      [ACTIVITY]: answer(200, { items: [], hasMore: false }),
      'GET /api/admin/orders': answer(200, { items: [], hasMore: false }),
    });
    const { router } = renderRoute('/admin/actividad');
    const menu = await sideMenu();
    expect(await menu.findByRole('link', { name: new RegExp(esAdmin.paidWaiting(1)) })).toBeInTheDocument();

    await router.navigate('/admin/pedidos');

    expect(await menu.findByRole('link', { name: new RegExp(esAdmin.paidWaiting(3)) })).toBeInTheDocument();
  });

  it('given no paid orders, or counts that cannot be read, when the menu shows, then "Pedidos" has no badge', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: answer(500), [ACTIVITY]: answer(200, { items: [], hasMore: false }) });
    renderRoute('/admin/actividad');

    const menu = await sideMenu();
    expect(menu.getByRole('link', { name: esAdmin.orders })).toHaveTextContent(new RegExp(`^${esAdmin.orders}$`));
  });

  it('given a phone, when "Menú" is pressed, then the panel opens with every group, and Escape closes it and returns the focus', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [COUNTS]: counts(0), [ACTIVITY]: answer(200, { items: [], hasMore: false }) });
    renderRoute('/admin/actividad');
    const button = await screen.findByRole('button', { name: esAdmin.menu });

    await userEvent.click(button);

    const panel = screen.getByRole('dialog', { name: esAdmin.title });
    for (const group of Object.values(esAdmin.navGroups)) {
      expect(within(panel).getByText(group)).toBeInTheDocument();
    }
    // jsdom has no Escape handling for dialogs: the browser sends a cancelable "cancel" event.
    fireEvent(panel, new Event('cancel', { cancelable: true }));
    expect(screen.queryByRole('dialog', { name: esAdmin.title })).not.toBeInTheDocument();
    expect(button).toHaveFocus();
  });

  it('given the open panel, when a link is chosen, then its page opens and the panel closes', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [COUNTS]: counts(0),
      [ACTIVITY]: answer(200, { items: [], hasMore: false }),
      'GET /api/admin/coupons': answer(200, { items: [], hasMore: false }),
    });
    const { router } = renderRoute('/admin/actividad');
    await userEvent.click(await screen.findByRole('button', { name: esAdmin.menu }));

    await userEvent.click(within(screen.getByRole('dialog', { name: esAdmin.title })).getByRole('link', { name: esAdmin.coupons }));

    expect(router.state.location.pathname).toBe('/admin/cupones');
    expect(screen.queryByRole('dialog', { name: esAdmin.title })).not.toBeInTheDocument();
  });
});
