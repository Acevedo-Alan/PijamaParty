import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { connectionLost, problem, shipping, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = es.admin.shippingPage;
const provinces = es.provinces;
const configured = answer(200, shipping({ SANTA_FE: 300_000, CORDOBA: 0 }, 5_000_000));

function zonesSection() {
  return within(screen.getByRole('region', { name: messages.zonesTitle }));
}

function freeShippingSection() {
  return within(screen.getByRole('region', { name: messages.freeShippingTitle }));
}

async function openPage() {
  renderRoute('/admin/envios');
  await screen.findByRole('region', { name: messages.zonesTitle });
}

describe('shipping configuration (UC-12)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given costs and a threshold, when the page opens, then every province shows its cost in pesos or nothing', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/shipping': configured,
    });

    await openPage();

    const zones = zonesSection();
    expect(zones.getAllByRole('textbox')).toHaveLength(24);
    expect(zones.getByLabelText(provinces.SANTA_FE)).toHaveValue('3000');
    expect(zones.getByLabelText(provinces.CORDOBA)).toHaveValue('0');
    expect(zones.getByLabelText(provinces.CIUDAD_DE_BUENOS_AIRES)).toHaveValue('');
    expect(freeShippingSection().getByLabelText(messages.threshold)).toHaveValue('50000');
  });

  it('given typed costs, when saved, then they go as cents and the empty provinces are left out', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/shipping': configured,
      'PUT /api/admin/shipping/zones': answer(200, shipping({ CORDOBA: 0, MENDOZA: 650_050 }, 5_000_000)),
    });
    await openPage();

    const zones = zonesSection();
    await userEvent.clear(zones.getByLabelText(provinces.SANTA_FE));
    await userEvent.type(zones.getByLabelText(provinces.MENDOZA), '6.500,50');
    await userEvent.click(zones.getByRole('button', { name: messages.saveZones }));

    expect(await zones.findByRole('status')).toHaveTextContent(es.admin.saved);
    expect(api.callsTo('PUT /api/admin/shipping/zones')[0]?.options.body).toEqual({
      zones: [
        { province: 'CORDOBA', costCents: 0 },
        { province: 'MENDOZA', costCents: 650_050 },
      ],
    });
    expect(zones.getByLabelText(provinces.MENDOZA)).toHaveValue('6500,50');
    expect(zones.getByLabelText(provinces.SANTA_FE)).toHaveValue('');
  });

  it('given costs the server refuses, when saved, then each of those provinces is marked', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/shipping': configured,
      'PUT /api/admin/shipping/zones': problem(422, {
        provinces: ['SANTA_FE'],
      }),
    });
    await openPage();

    const zones = zonesSection();
    await userEvent.clear(zones.getByLabelText(provinces.SANTA_FE));
    await userEvent.type(zones.getByLabelText(provinces.SANTA_FE), '20.000.000');
    await userEvent.click(zones.getByRole('button', { name: messages.saveZones }));

    expect(await zones.findByRole('alert')).toHaveTextContent(messages.invalidZones);
    expect(zones.getByLabelText(provinces.SANTA_FE)).toHaveAttribute('aria-invalid', 'true');
    expect(zones.getByText(messages.invalidCost)).toBeInTheDocument();
    expect(zones.getByLabelText(provinces.CORDOBA)).toHaveAttribute('aria-invalid', 'false');
  });

  it('given a cost that is not an amount, when saved, then it is marked and nothing is sent', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/shipping': configured,
    });
    await openPage();

    const zones = zonesSection();
    await userEvent.type(zones.getByLabelText(provinces.SALTA), 'mucho');
    await userEvent.click(zones.getByRole('button', { name: messages.saveZones }));

    expect(await zones.findByRole('alert')).toHaveTextContent(messages.invalidZones);
    expect(zones.getByLabelText(provinces.SALTA)).toHaveAttribute('aria-invalid', 'true');
    expect(api.callsTo('PUT /api/admin/shipping/zones')).toHaveLength(0);
  });

  it('given a threshold, when changed and then emptied, then the cents and then null are sent', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/shipping': configured,
      'PUT /api/admin/shipping/free-shipping': [
        answer(200, shipping({ SANTA_FE: 300_000, CORDOBA: 0 }, 6_000_000)),
        answer(200, shipping({ SANTA_FE: 300_000, CORDOBA: 0 }, null)),
      ],
    });
    await openPage();

    const free = freeShippingSection();
    const field = free.getByLabelText(messages.threshold);
    await userEvent.clear(field);
    await userEvent.type(field, '60.000');
    await userEvent.click(free.getByRole('button', { name: messages.saveThreshold }));
    expect(await free.findByRole('status')).toHaveTextContent(es.admin.saved);

    await userEvent.clear(field);
    await userEvent.click(free.getByRole('button', { name: messages.saveThreshold }));

    await vi.waitFor(() => {
      expect(api.callsTo('PUT /api/admin/shipping/free-shipping')).toHaveLength(2);
    });
    expect(api.callsTo('PUT /api/admin/shipping/free-shipping').map((call) => call.options.body)).toEqual([
      { thresholdCents: 6_000_000 },
      { thresholdCents: null },
    ]);
    expect(field).toHaveValue('');
  });

  it('given a threshold the server refuses, when saved, then it is marked with its range', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/shipping': configured,
      'PUT /api/admin/shipping/free-shipping': problem(422),
    });
    await openPage();

    const free = freeShippingSection();
    await userEvent.clear(free.getByLabelText(messages.threshold));
    await userEvent.type(free.getByLabelText(messages.threshold), '0');
    await userEvent.click(free.getByRole('button', { name: messages.saveThreshold }));

    expect(await free.findByRole('alert')).toHaveTextContent(messages.invalidThreshold);
    expect(free.getByLabelText(messages.threshold)).toHaveAttribute('aria-invalid', 'true');
  });

  it('given a lost connection, when saving the costs, then it asks to try again', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/shipping': configured,
      'PUT /api/admin/shipping/zones': connectionLost(),
    });
    await openPage();

    await userEvent.click(zonesSection().getByRole('button', { name: messages.saveZones }));

    expect(await zonesSection().findByRole('alert')).toHaveTextContent(es.admin.actionError);
  });

  it('given the configuration cannot be loaded, when the page opens, then it offers to retry', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      'GET /api/admin/shipping': problem(500),
    });

    renderRoute('/admin/envios');

    expect(await screen.findByText(es.admin.loadError)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: es.admin.retry })).toBeEnabled();
  });
});
