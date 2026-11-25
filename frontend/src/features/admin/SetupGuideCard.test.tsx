import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import type { SetupEntry, SetupItem } from './setupGuideApi.ts';

const messages = esAdmin.dashboard.setupGuide;
const GUIDE = 'GET /api/admin/setup-guide';
const ITEMS: SetupItem[] = ['WHATSAPP', 'EMAIL', 'SIZE_GUIDES', 'PRODUCT_PHOTOS', 'INSTAGRAM', 'HOME_BANNER'];

/** Every item done but the ones given. */
function guide(changes: Partial<Record<SetupItem, Partial<SetupEntry>>> = {}) {
  return answer(200, {
    items: ITEMS.map((item) => ({ item, state: 'DONE', total: 0, names: [], ...changes[item] })),
  });
}

/** The summary around the card: its other reads fail quietly, as they are not what these tests look at. */
function stub(routes: Parameters<typeof stubApi>[0]) {
  return stubApi({
    'GET /api/admin/auth/me': signedIn,
    'GET /api/admin/dashboard': answer(500),
    'GET /api/admin/activity': answer(200, { items: [], hasMore: false }),
    ...routes,
  });
}

const card = () => within(screen.getByRole('region', { name: messages.title }));

describe('"Tu tienda, lista" on the summary (UC-46)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a store without WhatsApp, when the summary opens, then the card lists it with its reason, linking to "Datos de la tienda"', async () => {
    stub({ [GUIDE]: guide({ WHATSAPP: { state: 'PENDING' } }) });

    renderRoute('/admin');

    const link = await screen.findByRole('link', { name: messages.items.WHATSAPP.title });
    expect(link).toHaveAttribute('href', '/admin/datos-de-la-tienda');
    expect(card().getByText(messages.items.WHATSAPP.why)).toBeInTheDocument();
    expect(card().getByText(messages.ready(5, 6))).toBeInTheDocument();
    // A store needs it: it cannot be omitted.
    expect(card().queryByRole('button', { name: messages.omitLabel(messages.items.WHATSAPP.title) })).toBeNull();
  });

  it('given "Ropa de casa" without a size guide and 7 products without photos, when the summary opens, then each name links to its screen and "y 2 más" to the list', async () => {
    const products = Array.from({ length: 5 }, (_, index) => ({ id: `p-${String(index)}`, name: `Pijama ${String(index)}` }));
    stub({
      [GUIDE]: guide({
        SIZE_GUIDES: { state: 'PENDING', total: 1, names: [{ id: 'c-1', name: 'Ropa de casa' }] },
        PRODUCT_PHOTOS: { state: 'PENDING', total: 7, names: products },
      }),
    });

    renderRoute('/admin');

    expect(await screen.findByRole('link', { name: 'Ropa de casa' })).toHaveAttribute(
      'href', '/admin/categorias/c-1/guia-de-talles');
    expect(card().getByRole('link', { name: 'Pijama 0' })).toHaveAttribute('href', '/admin/productos/p-0');
    expect(card().getByRole('link', { name: messages.more(2) })).toHaveAttribute('href', '/admin/productos');
  });

  it('given the banner hidden, when "Omitir" is pressed, then the server keeps it omitted and the card offers to show it again', async () => {
    const api = stub({
      [GUIDE]: [guide({ HOME_BANNER: { state: 'PENDING' }, WHATSAPP: { state: 'PENDING' } }),
        guide({ HOME_BANNER: { state: 'OMITTED' }, WHATSAPP: { state: 'PENDING' } })],
      'PUT /api/admin/setup-guide/omitted/{item}': answer(204),
      'DELETE /api/admin/setup-guide/omitted/{item}': answer(204),
    });
    renderRoute('/admin');

    await userEvent.click(await screen.findByRole('button', {
      name: messages.omitLabel(messages.items.HOME_BANNER.title) }));

    await waitFor(() => {
      expect(card().queryByRole('link', { name: messages.items.HOME_BANNER.title })).toBeNull();
    });
    expect(api.callsTo('PUT /api/admin/setup-guide/omitted/{item}')[0]?.options)
      .toEqual({ params: { path: { item: 'HOME_BANNER' } } });
    await userEvent.click(card().getByRole('button', { name: messages.showOmitted(1) }));
    await userEvent.click(card().getByRole('button', { name: messages.showAgainLabel(messages.items.HOME_BANNER.title) }));
    expect(api.callsTo('DELETE /api/admin/setup-guide/omitted/{item}')[0]?.options)
      .toEqual({ params: { path: { item: 'HOME_BANNER' } } });
  });

  it('given a failed "Omitir", when it answers, then the card says the change was not saved', async () => {
    stub({
      [GUIDE]: guide({ INSTAGRAM: { state: 'PENDING' } }),
      'PUT /api/admin/setup-guide/omitted/{item}': answer(500),
    });
    renderRoute('/admin');

    await userEvent.click(await screen.findByRole('button', {
      name: messages.omitLabel(messages.items.INSTAGRAM.title) }));

    expect(await card().findByRole('alert')).toHaveTextContent(messages.saveError);
    expect(card().getByRole('link', { name: messages.items.INSTAGRAM.title })).toBeInTheDocument();
  });

  it('given everything done, when the summary opens, then the card is one line with the chibi celebrating', async () => {
    stub({ [GUIDE]: guide() });

    renderRoute('/admin');

    expect(await screen.findByText(messages.allDone)).toBeInTheDocument();
    expect(card().queryByRole('list')).toBeNull();
  });

  it('given the guide failing, when the summary opens, then the card says so inside it, with "Reintentar"', async () => {
    stub({ [GUIDE]: answer(500) });

    renderRoute('/admin');

    // Its own read and its own message: the summary's error is another one (DashboardPage.test.tsx).
    expect(await screen.findByText(messages.loadError)).toBeInTheDocument();
    expect(card().getByText(messages.loadError)).toBeInTheDocument();
    expect(card().getByRole('button', { name: esAdmin.retry })).toBeInTheDocument();
  });
});
