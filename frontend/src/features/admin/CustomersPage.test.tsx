import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = esAdmin.customerList;
const CUSTOMERS = 'GET /api/admin/customers';

function customer(id: string, name: string, email: string, orders: number) {
  return { id, name, email, registeredAt: '2026-10-05T15:00:00Z', orders };
}

describe('customers (UC-34, T-E8-10)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given customers, when "Clientes" opens, then each shows its name, email, registration date and orders', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [CUSTOMERS]: [
        answer(200, { items: [customer('u-2', 'Juan Pérez', 'juan@example.com', 0)], hasMore: true }),
        answer(200, { items: [customer('u-1', 'Ana Gómez', 'ana@example.com', 3)], hasMore: false }),
      ],
    });
    renderRoute('/admin/clientes');

    const juan = within((await screen.findByText('Juan Pérez')).closest('li') as HTMLElement);
    expect(juan.getByText(messages.orders(0))).toBeInTheDocument();
    expect(
      juan.getByText(`juan@example.com · ${messages.registered(formatDate(new Date('2026-10-05T15:00:00Z')))}`),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: esAdmin.showMore }));

    const ana = within((await screen.findByText('Ana Gómez')).closest('li') as HTMLElement);
    expect(ana.getByText(messages.orders(3))).toBeInTheDocument();
    expect(api.callsTo(CUSTOMERS).map((call) => call.options.params?.query)).toEqual([{ page: 0 }, { page: 1 }]);
  });

  it('given a search typed, when submitted, then it goes trimmed and stays in the address', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [CUSTOMERS]: answer(200, { items: [customer('u-1', 'Ana Gómez', 'ana@example.com', 3)], hasMore: false }),
    });
    const { router } = renderRoute('/admin/clientes');
    const filters = within(await screen.findByRole('search', { name: messages.filters }));

    await userEvent.type(filters.getByLabelText(messages.search), '  gómez ');
    await userEvent.click(filters.getByRole('button', { name: messages.apply }));

    await vi.waitFor(() => {
      expect(api.callsTo(CUSTOMERS).at(-1)?.options.params?.query).toEqual({ page: 0, q: 'gómez' });
    });
    expect(new URLSearchParams(router.state.location.search).get('buscar')).toBe('gómez');
    expect(filters.getByLabelText(messages.search)).toHaveAccessibleDescription(messages.searchHint);
  });

  it('given dates in the address, when the list opens, then they are asked for and nothing matching says so', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [CUSTOMERS]: answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/clientes?desde=2026-10-01&hasta=2026-10-15&buscar=x');

    expect(await screen.findByText(messages.emptyFiltered)).toBeInTheDocument();
    expect(api.callsTo(CUSTOMERS)[0]?.options.params?.query).toEqual({
      page: 0,
      from: '2026-10-01',
      to: '2026-10-15',
    });
    expect(screen.getByLabelText(messages.from)).toHaveValue('2026-10-01');
  });

  it('given reversed dates, when chosen, then nothing is asked for and the field says why', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [CUSTOMERS]: answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/clientes?desde=2026-10-16&hasta=2026-10-15');

    expect(await screen.findByText(messages.invalidPeriod)).toBeInTheDocument();
    expect(screen.getByLabelText(messages.from)).toHaveAttribute('aria-invalid', 'true');
    expect(api.callsTo(CUSTOMERS)).toHaveLength(0);
  });

  it('given a search of one letter, when submitted, then the field says why, takes the focus and nothing is asked', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [CUSTOMERS]: answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/clientes');
    await screen.findByText(messages.empty);

    await userEvent.type(screen.getByLabelText(messages.search), 'a');
    await userEvent.click(screen.getByRole('button', { name: messages.apply }));

    const field = screen.getByLabelText(messages.search);
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field).toHaveFocus();
    expect(api.callsTo(CUSTOMERS)).toHaveLength(1);
  });
});
