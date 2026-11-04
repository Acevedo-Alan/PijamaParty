import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { connectionLost, problem, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = esAdmin.storeSettingsPage;
const labels = messages.labels;

const STORED = {
  email: null,
  whatsAppNumber: '5493425304507',
  openingHours: null,
  address: null,
  instagramUrl: 'https://www.instagram.com/pijamaparty.zzz/',
  facebookUrl: null,
  tiktokUrl: null,
  announcement: null,
  lowStockThreshold: 3,
  version: 4,
};

const rates = answer(200, { zones: [], freeShippingThresholdCents: 5_000_000 });

async function openPage(routes: Record<string, unknown>) {
  const api = stubApi({
    'GET /api/admin/auth/me': signedIn,
    'GET /api/shipping/rates': rates,
    'GET /api/admin/store/settings': answer(200, STORED),
    ...routes,
  });
  renderRoute('/admin/datos-de-la-tienda');
  await screen.findByRole('region', { name: messages.contactTitle });
  return api;
}

const strip = () => within(screen.getByRole('region', { name: messages.stripTitle }));

describe('store settings (UC-36)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the stored settings, when the page opens, then each field shows its value and empty ones are blank', async () => {
    await openPage({});

    expect(screen.getByRole('heading', { level: 1, name: messages.title })).toBeInTheDocument();
    expect(screen.getByLabelText(labels.whatsAppNumber)).toHaveValue('5493425304507');
    expect(screen.getByLabelText(labels.instagramUrl)).toHaveValue('https://www.instagram.com/pijamaparty.zzz/');
    expect(screen.getByLabelText(labels.email)).toHaveValue('');
    expect(screen.getByLabelText(labels.lowStockThreshold)).toHaveValue('3');
    expect(screen.getByLabelText(labels.announcement)).toHaveAccessibleDescription(messages.hints.announcement);
  });

  it('given every value typed, when saved, then they go trimmed with the version read, empty ones as null', async () => {
    const api = await openPage({
      'PUT /api/admin/store/settings': answer(200, { ...STORED, whatsAppNumber: '5493424000000', version: 5 }),
    });

    await userEvent.clear(screen.getByLabelText(labels.whatsAppNumber));
    await userEvent.type(screen.getByLabelText(labels.whatsAppNumber), ' +54 9 342 400-0000 ');
    await userEvent.type(screen.getByLabelText(labels.facebookUrl), 'https://www.facebook.com/pijamaparty');
    await userEvent.clear(screen.getByLabelText(labels.lowStockThreshold));
    await userEvent.type(screen.getByLabelText(labels.lowStockThreshold), '0');
    await userEvent.click(screen.getByRole('button', { name: esAdmin.save }));

    expect(await screen.findByRole('status')).toHaveTextContent(esAdmin.saved);
    expect(api.callsTo('PUT /api/admin/store/settings')[0]?.options.body).toEqual({
      email: null,
      whatsAppNumber: '+54 9 342 400-0000',
      openingHours: null,
      address: null,
      instagramUrl: 'https://www.instagram.com/pijamaparty.zzz/',
      facebookUrl: 'https://www.facebook.com/pijamaparty',
      tiktokUrl: null,
      announcement: null,
      lowStockThreshold: 0,
      version: 4,
    });
    expect(screen.getByLabelText(labels.whatsAppNumber)).toHaveValue('5493424000000');
  });

  it('given values the server refuses, when saved, then each field says why and is marked invalid', async () => {
    await openPage({
      'PUT /api/admin/store/settings': problem(422, {
        problem: 'INVALID_SETTINGS',
        fields: ['instagramUrl', 'lowStockThreshold'],
      }),
    });

    await userEvent.clear(screen.getByLabelText(labels.instagramUrl));
    await userEvent.type(screen.getByLabelText(labels.instagramUrl), 'https://evil.example/instagram.com');
    await userEvent.click(screen.getByRole('button', { name: esAdmin.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.invalid);
    const instagram = screen.getByLabelText(labels.instagramUrl);
    expect(instagram).toHaveAttribute('aria-invalid', 'true');
    expect(instagram).toHaveAccessibleDescription(`${messages.hints.instagramUrl} ${messages.errors.instagramUrl}`);
    expect(screen.getByLabelText(labels.lowStockThreshold)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(labels.email)).toHaveAttribute('aria-invalid', 'false');
  });

  it('given a threshold that is not a whole number, when saved, then it is refused without asking the server', async () => {
    const api = await openPage({});

    await userEvent.clear(screen.getByLabelText(labels.lowStockThreshold));
    await userEvent.type(screen.getByLabelText(labels.lowStockThreshold), '2,5');
    await userEvent.click(screen.getByRole('button', { name: esAdmin.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.invalid);
    expect(screen.getByLabelText(labels.lowStockThreshold)).toHaveAttribute('aria-invalid', 'true');
    expect(api.callsTo('PUT /api/admin/store/settings')).toHaveLength(0);
  });

  it('given another tab saved first, when saved, then the page says so and shows what is stored now', async () => {
    await openPage({
      'GET /api/admin/store/settings': [
        answer(200, STORED),
        answer(200, { ...STORED, announcement: 'Envío gratis a todo Santa Fe', version: 5 }),
      ],
      'PUT /api/admin/store/settings': problem(409, { problem: 'SETTINGS_CHANGED' }),
    });

    await userEvent.type(screen.getByLabelText(labels.address), 'Bv. Gálvez 1500');
    await userEvent.click(screen.getByRole('button', { name: esAdmin.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.changed);
    expect(screen.getByLabelText(labels.announcement)).toHaveValue('Envío gratis a todo Santa Fe');
    expect(screen.getByLabelText(labels.address)).toHaveValue('');
  });

  it('given the connection fails, when saved, then the page says it could not save', async () => {
    await openPage({ 'PUT /api/admin/store/settings': connectionLost });

    await userEvent.click(screen.getByRole('button', { name: esAdmin.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(esAdmin.actionError);
  });

  it('given an announcement, when typed, then the preview shows it instead of the free shipping; empty, the free shipping', async () => {
    await openPage({});

    expect(await strip().findByText(/^Envío gratis desde \$\s50\.000$/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(labels.announcement), 'Envío gratis a todo Santa Fe');

    expect(strip().getByText('Envío gratis a todo Santa Fe')).toBeInTheDocument();
    expect(strip().queryByText(/50\.000/)).not.toBeInTheDocument();
    expect(strip().getByText(es.header.ticker.carrier)).toBeInTheDocument();
  });
});
