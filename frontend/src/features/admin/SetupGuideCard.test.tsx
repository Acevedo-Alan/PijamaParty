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

/** Every item done but the ones given; the API still sends the first names, which the card no longer shows. */
function guide(changes: Partial<Record<SetupItem, Partial<SetupEntry> & { names?: { id: string; name: string }[] }>> = {}) {
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

async function expand() {
  await userEvent.click(await screen.findByRole('button', { name: messages.showSteps }));
}

describe('"Terminá de configurar tu tienda" on the summary (UC-46, T-E8-32)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given 2 of 6 items ready, when the summary opens, then the card is one line with its progress and the steps hidden behind "Ver pasos"', async () => {
    stub({ [GUIDE]: guide({
      WHATSAPP: { state: 'PENDING' }, EMAIL: { state: 'PENDING' },
      SIZE_GUIDES: { state: 'PENDING', total: 2 }, PRODUCT_PHOTOS: { state: 'PENDING', total: 286 },
    }) });

    renderRoute('/admin');

    const toggle = await screen.findByRole('button', { name: messages.showSteps });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    const progress = card().getByRole('progressbar', { name: messages.ready(2, 6) });
    expect(progress).toHaveAttribute('value', '2');
    expect(progress).toHaveAttribute('max', '6');
    expect(card().queryByRole('link', { name: messages.actionLabel(messages.configure, messages.items.WHATSAPP.title) }))
      .toBeNull();
  });

  it('given 286 published products without a photo, when the steps are shown, then the row counts them, names none and leads to the list; a done item has no action', async () => {
    stub({ [GUIDE]: guide({
      PRODUCT_PHOTOS: { state: 'PENDING', total: 286, names: [{ id: 'p-1', name: 'TOALLON KUROMI' }] },
    }) });
    renderRoute('/admin');

    await expand();

    const photos = messages.items.PRODUCT_PHOTOS;
    expect(card().getByText('(286)')).toBeInTheDocument();
    expect(card().queryByText('TOALLON KUROMI')).toBeNull();
    expect(card().getByRole('link', { name: messages.actionLabel(photos.action, photos.title) }))
      .toHaveAttribute('href', '/admin/productos');
    // WhatsApp is done: it reads as done and offers nothing.
    const whatsApp = card().getByText(messages.items.WHATSAPP.title).closest('li');
    expect(whatsApp).toHaveTextContent(messages.states.DONE);
    expect(within(whatsApp as HTMLElement).queryByRole('link')).toBeNull();
    expect(within(whatsApp as HTMLElement).queryByRole('button')).toBeNull();
  });

  it('given the steps shown once, when the summary opens again, then they are still shown', async () => {
    stub({ [GUIDE]: guide({ EMAIL: { state: 'PENDING' } }) });
    const first = renderRoute('/admin');
    await expand();
    first.unmount();

    renderRoute('/admin');

    expect(await screen.findByRole('button', { name: messages.hideSteps })).toHaveAttribute('aria-expanded', 'true');
  });

  it('given a store without WhatsApp, when the steps are shown, then its row says why and offers "Configurar", but not "Omitir"', async () => {
    stub({ [GUIDE]: guide({ WHATSAPP: { state: 'PENDING' } }) });
    renderRoute('/admin');

    await expand();

    const title = messages.items.WHATSAPP.title;
    expect(card().getByRole('link', { name: messages.actionLabel(messages.configure, title) }))
      .toHaveAttribute('href', '/admin/datos-de-la-tienda');
    expect(card().getByText(messages.items.WHATSAPP.why)).toBeInTheDocument();
    expect(card().queryByRole('button', { name: messages.omitLabel(title) })).toBeNull();
  });

  it('given the banner pending, when "Omitir" is pressed, then the server keeps it omitted and its row offers to show it again', async () => {
    const api = stub({
      [GUIDE]: [guide({ HOME_BANNER: { state: 'PENDING' }, WHATSAPP: { state: 'PENDING' } }),
        guide({ HOME_BANNER: { state: 'OMITTED' }, WHATSAPP: { state: 'PENDING' } })],
      'PUT /api/admin/setup-guide/omitted/{item}': answer(204),
      'DELETE /api/admin/setup-guide/omitted/{item}': answer(204),
    });
    renderRoute('/admin');
    await expand();
    const banner = messages.items.HOME_BANNER.title;

    await userEvent.click(card().getByRole('button', { name: messages.omitLabel(banner) }));

    const showAgain = await card().findByRole('button', { name: messages.showAgainLabel(banner) });
    expect(api.callsTo('PUT /api/admin/setup-guide/omitted/{item}')[0]?.options)
      .toEqual({ params: { path: { item: 'HOME_BANNER' } } });
    expect(showAgain.closest('li')).toHaveTextContent(messages.states.OMITTED);
    await userEvent.click(showAgain);
    await waitFor(() => {
      expect(api.callsTo('DELETE /api/admin/setup-guide/omitted/{item}')[0]?.options)
        .toEqual({ params: { path: { item: 'HOME_BANNER' } } });
    });
  });

  it('given a failed "Omitir", when it answers, then the card says the change was not saved', async () => {
    stub({
      [GUIDE]: guide({ INSTAGRAM: { state: 'PENDING' } }),
      'PUT /api/admin/setup-guide/omitted/{item}': answer(500),
    });
    renderRoute('/admin');
    await expand();

    await userEvent.click(card().getByRole('button', { name: messages.omitLabel(messages.items.INSTAGRAM.title) }));

    expect(await card().findByRole('alert')).toHaveTextContent(messages.saveError);
  });

  it('given everything done, when the summary opens, then the card is one line with the chibi celebrating and nothing to expand', async () => {
    stub({ [GUIDE]: guide() });

    renderRoute('/admin');

    expect(await screen.findByRole('region', { name: messages.allDone })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: messages.showSteps })).toBeNull();
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
