import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { answer, NO_CART, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import type { SavedAddress } from './addressBookApi.ts';

const messages = es.account.addresses;
const fields = es.checkout.fields;
const ANA = { id: '00000000-0000-0000-0000-000000000001', email: 'ana@example.com', name: 'Ana Pérez' };

function saved(id: string, street: string, primary: boolean, overrides: Partial<SavedAddress> = {}): SavedAddress {
  return {
    id,
    street,
    number: '1234',
    floor: null,
    apartment: null,
    city: 'Santa Fe',
    province: 'SANTA_FE',
    postalCode: 'S3000ABC',
    primary,
    ...overrides,
  };
}

/** The i-th element, which the test expects to exist. */
function nth(items: HTMLElement[], index: number): HTMLElement {
  const item = items[index];
  if (item === undefined) {
    throw new Error(`No element at ${String(index)}`);
  }
  return item;
}

/** The customer is signed in and GET /api/account/addresses answers what `book` holds at that moment. */
function stubBook(initial: SavedAddress[]) {
  const book = { addresses: initial };
  const get = vi.spyOn(api, 'GET').mockImplementation(
    withHeader(
      (path) => Promise.resolve(path === '/api/account/addresses' ? answer(200, book.addresses) : NO_CART),
      answer(200, ANA),
    ),
  );
  return { book, get };
}

async function fillAddress(street = 'San Martín', number = '1234', city = 'Santa Fe', postalCode = 's3000abc') {
  await userEvent.type(screen.getByLabelText(fields.street), street);
  await userEvent.type(screen.getByLabelText(fields.number), number);
  await userEvent.type(screen.getByLabelText(fields.city), city);
  await userEvent.selectOptions(screen.getByLabelText(fields.province), 'SANTA_FE');
  await userEvent.type(screen.getByLabelText(fields.postalCode), postalCode);
}

describe('address book (UC-20, T-E4-05)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a visitor who is not signed in, when it opens the address book, then it is sent to sign in', async () => {
    vi.spyOn(api, 'GET').mockImplementation(withHeader(() => Promise.resolve(NO_CART)));
    const { router } = renderRoute('/cuenta/direcciones');

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/cuenta/ingresar');
    });
  });

  it('given a signed-in customer, when it follows the header link, then it sees its addresses with the primary first', async () => {
    stubBook([saved('a1', 'Belgrano', true), saved('a2', 'San Martín', false, { floor: '3', apartment: 'B' })]);
    const { router } = renderRoute('/');

    await userEvent.click(await screen.findByRole('link', { name: es.account.addressesLink }));

    expect(router.state.location.pathname).toBe('/cuenta/direcciones');
    const items = await screen.findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Belgrano 1234');
    expect(within(nth(items, 0)).getByText(messages.primary)).toBeInTheDocument();
    expect(items[1]).toHaveTextContent('San Martín 1234, piso 3, depto. B');
    expect(items[1]).toHaveTextContent('Santa Fe, Santa Fe (S3000ABC)');
    expect(within(nth(items, 1)).queryByText(messages.primary)).not.toBeInTheDocument();
  });

  it('given a customer without addresses, when it adds the first one, then it is sent as typed and the list shows it', async () => {
    const { book } = stubBook([]);
    const post = vi.spyOn(api, 'POST').mockImplementation((() => {
      book.addresses = [saved('a1', 'San Martín', true)];
      return Promise.resolve(answer(201, book.addresses[0]));
    }) as never);
    renderRoute('/cuenta/direcciones');
    expect(await screen.findByText(messages.empty)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: messages.add }));
    expect(screen.queryByLabelText(messages.primaryCheckbox)).not.toBeInTheDocument();
    await fillAddress();
    await userEvent.click(screen.getByRole('button', { name: messages.save }));

    expect(await screen.findByRole('status')).toHaveTextContent(messages.saved);
    expect(post).toHaveBeenCalledWith('/api/account/addresses', {
      body: {
        street: 'San Martín',
        number: '1234',
        city: 'Santa Fe',
        province: 'SANTA_FE',
        postalCode: 's3000abc',
        primary: false,
      },
    });
    expect(await screen.findByText('San Martín 1234')).toBeInTheDocument();
  });

  it('given fields that break a rule, when the address is saved, then each one says what it needs and nothing is sent', async () => {
    stubBook([]);
    const post = vi.spyOn(api, 'POST');
    renderRoute('/cuenta/direcciones');
    await userEvent.click(await screen.findByRole('button', { name: messages.add }));

    await userEvent.type(screen.getByLabelText(fields.postalCode), '300');
    await userEvent.click(screen.getByRole('button', { name: messages.save }));

    expect(screen.getByLabelText(fields.street)).toHaveFocus();
    expect(screen.getByLabelText(fields.street)).toHaveAccessibleDescription(es.checkout.errors.street);
    expect(screen.getByLabelText(fields.postalCode)).toHaveAccessibleDescription(es.checkout.errors.postalCode);
    expect(screen.getByLabelText(fields.province)).toHaveAccessibleDescription(es.checkout.errors.province);
    expect(screen.getByLabelText(fields.floor, { exact: false })).not.toHaveAccessibleDescription();
    expect(post).not.toHaveBeenCalled();
  });

  it('given fields the server refuses, when the address is saved, then the fields it names are marked', async () => {
    stubBook([]);
    vi.spyOn(api, 'POST').mockResolvedValue(
      answer(422, { problem: 'INVALID_ADDRESS', fields: ['city', 'unknown'] }),
    );
    renderRoute('/cuenta/direcciones');
    await userEvent.click(await screen.findByRole('button', { name: messages.add }));

    await fillAddress();
    await userEvent.click(screen.getByRole('button', { name: messages.save }));

    await waitFor(() => {
      expect(screen.getByLabelText(fields.city)).toHaveFocus();
    });
    expect(screen.getByLabelText(fields.city)).toHaveAccessibleDescription(es.checkout.errors.city);
    expect(screen.getByLabelText(fields.street)).not.toHaveAccessibleDescription();
  });

  it('given a customer with 10 addresses, when it looks for adding another, then it is told the book is full', async () => {
    stubBook(Array.from({ length: 10 }, (_, i) => saved(`a${String(i)}`, `Calle ${String(i)}`, i === 0)));
    renderRoute('/cuenta/direcciones');

    expect(await screen.findByText(messages.full)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: messages.add })).not.toBeInTheDocument();
  });

  it('given a book that filled up elsewhere, when the address is saved, then the refusal says it is full', async () => {
    stubBook([saved('a1', 'Belgrano', true)]);
    vi.spyOn(api, 'POST').mockResolvedValue(answer(422, { problem: 'ADDRESS_BOOK_FULL' }));
    renderRoute('/cuenta/direcciones');
    await userEvent.click(await screen.findByRole('button', { name: messages.add }));

    await fillAddress();
    await userEvent.click(screen.getByRole('button', { name: messages.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.full);
  });

  it('given an address marked as primary, when another one is marked, then the previous one stops being primary', async () => {
    const { book } = stubBook([saved('a1', 'Belgrano', true), saved('a2', 'San Martín', false)]);
    const put = vi.spyOn(api, 'PUT').mockImplementation(() => {
      book.addresses = [saved('a2', 'San Martín', true), saved('a1', 'Belgrano', false)];
      return Promise.resolve(answer(200, book.addresses[0]));
    });
    renderRoute('/cuenta/direcciones');

    await userEvent.click(await screen.findByRole('button', { name: messages.makePrimary('San Martín 1234') }));

    expect(await screen.findByRole('status')).toHaveTextContent(messages.primaryChanged);
    expect(put).toHaveBeenCalledWith('/api/account/addresses/{id}', {
      params: { path: { id: 'a2' } },
      body: {
        street: 'San Martín',
        number: '1234',
        city: 'Santa Fe',
        province: 'SANTA_FE',
        postalCode: 'S3000ABC',
        primary: true,
      },
    });
    await waitFor(() => {
      expect(screen.getAllByRole('listitem')[0]).toHaveTextContent('San Martín 1234');
    });
    expect(within(nth(screen.getAllByRole('listitem'), 1)).queryByText(messages.primary)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: messages.makePrimary('San Martín 1234') })).not.toBeInTheDocument();
  });

  it('given an address, when it is edited, then the form starts with its fields and every field is sent', async () => {
    const { book } = stubBook([saved('a1', 'Belgrano', true), saved('a2', 'San Martín', false)]);
    const put = vi.spyOn(api, 'PUT').mockImplementation(() => {
      book.addresses = [saved('a1', 'Belgrano', true), saved('a2', 'Rivadavia', false)];
      return Promise.resolve(answer(200, book.addresses[1]));
    });
    renderRoute('/cuenta/direcciones');

    await userEvent.click(await screen.findByRole('button', { name: messages.edit('San Martín 1234') }));
    expect(screen.getByRole('heading', { name: messages.editTitle })).toHaveFocus();
    expect(screen.getByLabelText(fields.street)).toHaveValue('San Martín');
    await userEvent.clear(screen.getByLabelText(fields.street));
    await userEvent.type(screen.getByLabelText(fields.street), 'Rivadavia');
    await userEvent.click(screen.getByLabelText(messages.primaryCheckbox));
    await userEvent.click(screen.getByRole('button', { name: messages.save }));

    expect(await screen.findByRole('status')).toHaveTextContent(messages.saved);
    expect(put).toHaveBeenCalledWith('/api/account/addresses/{id}', {
      params: { path: { id: 'a2' } },
      body: {
        street: 'Rivadavia',
        number: '1234',
        city: 'Santa Fe',
        province: 'SANTA_FE',
        postalCode: 'S3000ABC',
        primary: true,
      },
    });
  });

  it('given the primary, when it is edited, then there is no checkbox to make it primary', async () => {
    stubBook([saved('a1', 'Belgrano', true)]);
    renderRoute('/cuenta/direcciones');

    await userEvent.click(await screen.findByRole('button', { name: messages.edit('Belgrano 1234') }));

    expect(screen.queryByLabelText(messages.primaryCheckbox)).not.toBeInTheDocument();
  });

  it('given an address, when it is deleted after confirming, then it leaves the list', async () => {
    const { book } = stubBook([saved('a1', 'Belgrano', true), saved('a2', 'San Martín', false)]);
    const remove = vi.spyOn(api, 'DELETE').mockImplementation((() => {
      book.addresses = [saved('a1', 'Belgrano', true)];
      return Promise.resolve(answer(204));
    }) as never);
    renderRoute('/cuenta/direcciones');

    await userEvent.click(await screen.findByRole('button', { name: messages.remove('San Martín 1234') }));
    expect(remove).not.toHaveBeenCalled();
    expect(screen.getByText(messages.confirmRemove)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: messages.confirmRemoveYes }));

    expect(await screen.findByRole('status')).toHaveTextContent(messages.removed);
    expect(remove).toHaveBeenCalledWith('/api/account/addresses/{id}', { params: { path: { id: 'a2' } } });
    await waitFor(() => {
      expect(screen.queryByText('San Martín 1234')).not.toBeInTheDocument();
    });
  });

  it('given the confirmation to delete, when it is cancelled, then nothing is deleted', async () => {
    stubBook([saved('a1', 'Belgrano', true)]);
    const remove = vi.spyOn(api, 'DELETE');
    renderRoute('/cuenta/direcciones');

    await userEvent.click(await screen.findByRole('button', { name: messages.remove('Belgrano 1234') }));
    await userEvent.click(screen.getByRole('button', { name: messages.cancel }));

    expect(screen.queryByText(messages.confirmRemove)).not.toBeInTheDocument();
    expect(screen.getByText('Belgrano 1234')).toBeInTheDocument();
    expect(remove).not.toHaveBeenCalled();
  });

  it('given an address deleted elsewhere, when it is marked primary, then the customer is told and the list refreshes', async () => {
    const { book, get } = stubBook([saved('a1', 'Belgrano', true), saved('a2', 'San Martín', false)]);
    vi.spyOn(api, 'PUT').mockImplementation(() => {
      book.addresses = [saved('a1', 'Belgrano', true)];
      return Promise.resolve(answer(404, { problem: 'ADDRESS_NOT_FOUND' }));
    });
    renderRoute('/cuenta/direcciones');

    await userEvent.click(await screen.findByRole('button', { name: messages.makePrimary('San Martín 1234') }));

    expect(await screen.findByRole('status')).toHaveTextContent(messages.notFound);
    await waitFor(() => {
      expect(screen.queryByText('San Martín 1234')).not.toBeInTheDocument();
    });
    expect((get.mock.calls as unknown[][]).filter(([path]) => path === '/api/account/addresses').length).toBeGreaterThan(1);
  });
});
