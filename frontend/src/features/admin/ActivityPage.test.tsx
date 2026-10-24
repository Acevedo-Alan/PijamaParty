import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { components } from '../../api/schema.d.ts';
import { es } from '../../i18n/es.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

type Row = components['schemas']['ActivityEntryResponse'];

const messages = es.admin.activityList;
const LIST = 'GET /api/admin/activity';

function row(id: string, overrides: Partial<Row> = {}): Row {
  return {
    id,
    occurredAt: '2026-10-15T18:30:00Z',
    area: 'CATALOG',
    action: 'VARIANT_UPDATED',
    subject: { type: 'VARIANT', id: 'v-1', label: 'PP-AB12CD34' },
    changes: [{ field: 'price', before: '$20.000', after: '$22.000' }],
    ...overrides,
  };
}

function card(title: string) {
  return within(screen.getAllByText(title)[0]?.closest('li') as HTMLElement);
}

describe('the activity log in the back office (UC-37)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a price change, when Actividad opens, then it shows the action, the area, the time, the SKU and the prices', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, { items: [row('a-1')], hasMore: false }),
    });
    renderRoute('/admin/actividad');

    await screen.findByText(messages.actions.VARIANT_UPDATED);
    const entry = card(messages.actions.VARIANT_UPDATED);
    expect(entry.getByText(messages.areas.CATALOG)).toBeInTheDocument();
    expect(entry.getByText('15/10/26, 15:30')).toHaveAttribute('datetime', '2026-10-15T18:30:00.000Z');
    expect(entry.getByText('PP-AB12CD34')).toBeInTheDocument();
    expect(entry.queryByRole('link', { name: 'PP-AB12CD34' })).not.toBeInTheDocument();
    expect(entry.getByText('price: $20.000 → $22.000')).toBeInTheDocument();
  });

  it('given entries about a product and an order, when listed, then each label links to its screen', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, {
        items: [
          row('a-1', {
            action: 'PRODUCT_HIDDEN',
            subject: { type: 'PRODUCT', id: 'p-1', label: 'Pijama Luna' },
            changes: [],
          }),
          row('a-2', {
            area: 'ORDERS',
            action: 'ORDER_STATUS_CHANGED',
            subject: { type: 'ORDER', id: 'o-1', label: '#1001' },
            changes: [{ field: 'status', before: 'PREPARING', after: 'SHIPPED' }],
          }),
        ],
        hasMore: false,
      }),
    });
    renderRoute('/admin/actividad');

    expect(await screen.findByRole('link', { name: 'Pijama Luna' })).toHaveAttribute('href', '/admin/productos/p-1');
    expect(screen.getByRole('link', { name: '#1001' })).toHaveAttribute('href', '/admin/pedidos/o-1');
    expect(card(messages.actions.PRODUCT_HIDDEN).queryByRole('list')).not.toBeInTheDocument();
  });

  it('given a change that set a value and one that cleared it, when listed, then the missing side shows a dash', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, {
        items: [
          row('a-1', {
            action: 'TRACKING_NUMBER_CORRECTED',
            subject: { type: 'ORDER', id: 'o-1', label: '#1001' },
            changes: [
              { field: 'trackingNumber', before: null, after: 'AR123' },
              { field: 'note', before: 'x', after: null },
            ],
          }),
        ],
        hasMore: false,
      }),
    });
    renderRoute('/admin/actividad');

    expect(await screen.findByText('trackingNumber: — → AR123')).toBeInTheDocument();
    expect(screen.getByText('note: x → —')).toBeInTheDocument();
  });

  it('given more than one page, when Ver más is pressed, then the next page is asked for and added', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: [
        answer(200, { items: [row('a-1')], hasMore: true }),
        answer(200, { items: [row('a-2', { action: 'STOCK_ADJUSTED', area: 'STOCK' })], hasMore: false }),
      ],
    });
    renderRoute('/admin/actividad');
    await screen.findByText(messages.actions.VARIANT_UPDATED);

    await userEvent.click(screen.getByRole('button', { name: es.admin.showMore }));

    expect(await screen.findByText(messages.actions.STOCK_ADJUSTED)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: es.admin.showMore })).not.toBeInTheDocument();
    expect(api.callsTo(LIST).map((call) => call.options.params?.query)).toEqual([{ page: 0 }, { page: 1 }]);
  });

  it('given the list, when filtered by area and dates, then they are asked for and kept in the address', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, { items: [row('a-1')], hasMore: false }),
    });
    const { router } = renderRoute('/admin/actividad');
    await screen.findByText(messages.actions.VARIANT_UPDATED);

    await userEvent.selectOptions(screen.getByLabelText(messages.area), messages.areas.STOCK);
    fireEvent.change(screen.getByLabelText(messages.from), { target: { value: '2026-10-01' } });
    fireEvent.change(screen.getByLabelText(messages.to), { target: { value: '2026-10-15' } });

    await vi.waitFor(() => {
      expect(api.callsTo(LIST).at(-1)?.options.params?.query).toEqual({
        page: 0,
        area: 'STOCK',
        from: '2026-10-01',
        to: '2026-10-15',
      });
    });
    expect(router.state.location.search).toBe('?seccion=STOCK&desde=2026-10-01&hasta=2026-10-15');
  });

  it('given a from date after the to date, when the page opens, then it says so and asks for nothing', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/actividad?desde=2026-10-15&hasta=2026-10-01');

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.invalidPeriod);
    expect(screen.getByLabelText(messages.from)).toHaveAttribute('aria-invalid', 'true');
    expect(api.callsTo(LIST)).toHaveLength(0);
  });

  it('given no entries, when the page opens with and without filters, then it says so for each', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [LIST]: answer(200, { items: [], hasMore: false }) });
    renderRoute('/admin/actividad');
    expect(await screen.findByText(messages.empty)).toBeInTheDocument();
  });

  it('given no entries in an area, when filtered, then it says there are none with these filters', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedIn, [LIST]: answer(200, { items: [], hasMore: false }) });
    renderRoute('/admin/actividad?seccion=CONTENT');

    expect(await screen.findByText(messages.emptyFiltered)).toBeInTheDocument();
    expect(screen.getByLabelText(messages.area)).toHaveValue('CONTENT');
  });

  it('given the list fails, when Reintentar is pressed, then it is asked for again', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: [answer(500, {}), answer(200, { items: [row('a-1')], hasMore: false })],
    });
    renderRoute('/admin/actividad');

    await userEvent.click(await screen.findByRole('button', { name: es.admin.retry }));

    expect(await screen.findByText(messages.actions.VARIANT_UPDATED)).toBeInTheDocument();
    expect(api.callsTo(LIST)).toHaveLength(2);
  });
});
