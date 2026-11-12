import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { answer, NO_CART, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { initials } from './AccountArea.tsx';
import { DATA_PATH, isAccountAreaPath } from './AccountLink.tsx';
import accountCss from './account.css?raw';

const area = es.account.area;
const summary = es.account.summary;
const myAccount = es.account.myAccount;
const ANA = { id: '00000000-0000-0000-0000-000000000001', email: 'ana@example.com', name: 'Ana Pérez', phone: null };

function order(number: number, status: string) {
  return {
    id: `00000000-0000-0000-0000-00000000a${String(number).padStart(3, '0')}`,
    number,
    createdAt: '2026-10-14T15:00:00Z',
    status,
    totalCents: 2_450_000,
  };
}

const PRIMARY = {
  id: '00000000-0000-0000-0000-0000000000d1',
  street: 'San Martín',
  number: '1234',
  floor: null,
  apartment: null,
  city: 'Santa Fe',
  province: 'SANTA_FE',
  postalCode: 'S3000ABC',
  primary: true,
};

interface Answers {
  orders?: unknown[];
  addresses?: unknown[];
  account?: unknown;
}

/** Ana is signed in, with these orders (newest first) and addresses; every other GET answers as with no cart. */
function stubAccount({ orders = [], addresses = [], account = ANA }: Answers = {}) {
  return vi.spyOn(api, 'GET').mockImplementation(
    withHeader((path) => {
      switch (path) {
        case '/api/account/orders':
          return Promise.resolve(answer(200, { orders, hasMore: false }));
        case '/api/account/addresses':
          return Promise.resolve(answer(200, addresses));
        case '/api/account/identities':
          return Promise.resolve(answer(200, { hasPassword: true, identities: [] }));
        case '/api/account/oauth2/providers':
          return Promise.resolve(answer(200, { providers: [] }));
        default:
          return Promise.resolve(NO_CART);
      }
    }, answer(200, account)),
  );
}

const navigation = () => screen.findByRole('navigation', { name: area.navigation });

describe('the account area (T-E7-60)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given names of one, two or three words, when their initials are taken, then they are the first two in upper case', () => {
    expect(initials('Ana Pérez')).toBe('AP');
    expect(initials('  cliente   prueba  extra ')).toBe('CP');
    expect(initials('Ana')).toBe('A');
  });

  it('given a signed-in customer, when an account page opens, then the navigation shows who it is and marks the page', async () => {
    stubAccount();
    renderRoute('/cuenta/direcciones');

    const nav = await navigation();
    expect(await within(nav).findByText('Ana Pérez')).toBeInTheDocument();
    expect(within(nav).getByText('ana@example.com')).toBeInTheDocument();
    expect(within(nav).getByText('AP')).toHaveAttribute('aria-hidden', 'true');
    const links = within(nav).getAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual([area.summary, area.orders, area.addresses, area.data]);
    expect(within(nav).getByRole('link', { name: area.addresses })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('link', { name: area.summary })).not.toHaveAttribute('aria-current');
    // The navigation is beside the page, not in its main content.
    expect(screen.getByRole('main')).not.toContainElement(nav);
  });

  it('given paths under /cuenta, when checked, then only the summary, data, addresses and orders are the area', () => {
    for (const path of ['/cuenta', '/cuenta/', DATA_PATH, '/cuenta/direcciones', '/cuenta/pedidos', '/cuenta/pedidos/x']) {
      expect(isAccountAreaPath(path)).toBe(true);
    }
    for (const path of ['/cuenta/ingresar', '/cuenta/registro', '/cuenta/vincular', '/', '/cuentas']) {
      expect(isAccountAreaPath(path)).toBe(false);
    }
  });

  it('given the navigation, when another section is chosen, then the navigation stays and only the page changes', async () => {
    stubAccount();
    const { router } = renderRoute('/cuenta/direcciones');
    const nav = await navigation();

    await userEvent.click(within(nav).getByRole('link', { name: area.orders }));

    expect(router.state.location.pathname).toBe('/cuenta/pedidos');
    expect(await navigation()).toBe(nav);
    expect(within(nav).getByRole('link', { name: area.orders })).toHaveAttribute('aria-current', 'page');
  });

  it('given its styles, when read, then the area is one column that never grows past the screen on a phone', () => {
    expect(/\.account-area \{[^}]*grid-template-columns: minmax\(0, 1fr\);/.test(accountCss)).toBe(true);
  });

  it("given an order's detail, when it opens, then \"Mis pedidos\" is the marked link", async () => {
    stubAccount();
    renderRoute('/cuenta/pedidos/00000000-0000-0000-0000-00000000a001');

    expect(within(await navigation()).getByRole('link', { name: area.orders })).toHaveAttribute('aria-current', 'page');
  });

  it('given the navigation, when "Salir" is pressed, then the page fades out, the customer signs out, goes home and gets the goodbye (T-E7-66)', async () => {
    stubAccount();
    const post = vi.spyOn(api, 'POST').mockResolvedValue(answer(204));
    const { router } = renderRoute('/cuenta/direcciones');

    await userEvent.click(within(await navigation()).getByRole('button', { name: es.account.signOut }));

    expect(post).toHaveBeenCalledWith('/api/account/logout');
    await waitFor(() => {
      expect(document.querySelector('.account-area-content')).toHaveAttribute('data-leaving');
    });
    expect(await screen.findByText(es.account.signedOutNotice)).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
    expect(router.state.historyAction).not.toBe('REPLACE');
  });
});

describe('the account summary (T-E7-61)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given 5 orders and a primary address, when /cuenta opens, then it shows the 3 newest, the address and the data, each with its link', async () => {
    stubAccount({
      orders: [order(1005, 'SHIPPED'), order(1004, 'PAID'), order(1003, 'DELIVERED'), order(1002, 'PAID'), order(1001, 'PAID')],
      addresses: [PRIMARY],
    });
    renderRoute('/cuenta');

    expect(await screen.findByRole('heading', { level: 1, name: es.account.greeting('Ana') })).toBeInTheDocument();
    const orders = within(screen.getByRole('region', { name: summary.recentOrders }));
    const cards = await orders.findAllByRole('link', { name: /Pedido #/ });
    expect(cards.map((card) => card.querySelector('.summary-order-number')?.textContent)).toEqual([
      es.tracking.title(1005),
      es.tracking.title(1004),
      es.tracking.title(1003),
    ]);
    expect(cards[0]).toHaveTextContent(es.tracking.statuses.SHIPPED);
    expect(orders.getByRole('link', { name: summary.allOrders })).toHaveAttribute('href', '/cuenta/pedidos');

    const address = within(screen.getByRole('region', { name: summary.primaryAddress }));
    expect(await address.findByText('San Martín 1234')).toBeInTheDocument();
    expect(address.getByRole('link', { name: summary.manageAddresses })).toHaveAttribute('href', '/cuenta/direcciones');

    const data = within(screen.getByRole('region', { name: summary.yourData }));
    expect(data.getByText('ana@example.com')).toBeInTheDocument();
    expect(data.getByText(summary.noPhone)).toBeInTheDocument();
    expect(data.getByRole('link', { name: summary.dataAndAccess })).toHaveAttribute('href', DATA_PATH);
  });

  it('given a customer without orders or addresses, when /cuenta opens, then each block says so and offers its link', async () => {
    stubAccount({ account: { ...ANA, phone: '3424000000' } });
    renderRoute('/cuenta');

    const orders = within(await screen.findByRole('region', { name: summary.recentOrders }));
    expect(await orders.findByText(es.account.orders.empty)).toBeInTheDocument();
    expect(orders.getByRole('link', { name: es.account.orders.goShopping })).toHaveAttribute('href', '/');
    const address = within(screen.getByRole('region', { name: summary.primaryAddress }));
    expect(await address.findByText(summary.noAddresses)).toBeInTheDocument();
    expect(address.getByRole('link', { name: summary.addAddress })).toHaveAttribute('href', '/cuenta/direcciones');
    expect(screen.getByText('3424000000')).toBeInTheDocument();
  });

  it('given a provider sent the customer back to /cuenta?social=linked, when it opens, then it goes on to "Datos y acceso" with the notice', async () => {
    stubAccount();
    const { router } = renderRoute('/cuenta?social=linked');

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(DATA_PATH);
    });
    expect(router.state.location.search).toBe('?social=linked');
    const notice = await screen.findByText(myAccount.social.linked);
    // The notice keeps the focus: the page's heading does not take it after the redirect.
    await waitFor(() => {
      expect(notice).toHaveFocus();
    });
  });
});

describe('"Datos y acceso" (T-E7-62)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a customer with a password, when "Cambiar contraseña" is pressed, then the recovery email goes to its own email and the page says so', async () => {
    stubAccount();
    const post = vi.spyOn(api, 'POST').mockResolvedValue(answer(204));
    renderRoute(DATA_PATH);

    await userEvent.click(await screen.findByRole('button', { name: myAccount.changePassword }));

    expect(post).toHaveBeenCalledWith('/api/account/password-recovery', { body: { email: 'ana@example.com' } });
    // T-E7-63: the answer shows inside "Formas de ingresar", under its card, and takes the focus.
    const methods = within(screen.getByRole('region', { name: myAccount.methodsTitle }));
    const sent = await methods.findByText(myAccount.changePasswordSent('ana@example.com'));
    expect(sent).toHaveFocus();
    expect(sent).toHaveClass('group-notice');
  });

  it('given the email just sent, when a minute passes, then "Cambiar contraseña por email" cannot be pressed until then (T-E7-63)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      stubAccount();
      const post = vi.spyOn(api, 'POST').mockResolvedValue(answer(204));
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime.bind(vi) });
      renderRoute(DATA_PATH);

      await user.click(await screen.findByRole('button', { name: myAccount.changePassword }));

      expect(await screen.findByText(myAccount.passwordLinkSent('1:00'))).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: myAccount.changePassword })).not.toBeInTheDocument();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1000);
      });
      expect(screen.getByText(myAccount.passwordLinkSent('0:59'))).toBeInTheDocument();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(59_000);
      });
      expect(screen.getByRole('button', { name: myAccount.changePassword })).toBeEnabled();
      expect(post).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it('given too many requests, when "Cambiar contraseña" is pressed, then the page says to wait and the row stays pressable', async () => {
    stubAccount();
    vi.spyOn(api, 'POST').mockResolvedValue(answer(429));
    renderRoute(DATA_PATH);

    await userEvent.click(await screen.findByRole('button', { name: myAccount.changePassword }));

    expect(await screen.findByText(es.account.tooManyAttempts)).toHaveClass('group-notice', 'is-problem');
    expect(screen.getByRole('button', { name: myAccount.changePassword })).toBeEnabled();
  });

  it('given a customer without a phone, when it adds "342 400-0000", then it is sent as typed and the stored digits show (UC-40)', async () => {
    stubAccount();
    const put = vi.spyOn(api, 'PUT').mockResolvedValue(answer(200, { phone: '3424000000' }));
    renderRoute(DATA_PATH);

    await userEvent.click(await screen.findByRole('button', { name: myAccount.addPhoneLabel }));
    const field = screen.getByLabelText(myAccount.phoneField);
    expect(field).toHaveFocus();
    expect(field).toHaveAttribute('autocomplete', 'tel');
    await userEvent.type(field, '342 400-0000');
    await userEvent.click(screen.getByRole('button', { name: myAccount.savePhone }));

    expect(put).toHaveBeenCalledWith('/api/account/phone', { body: { phone: '342 400-0000' } });
    const personal = within(screen.getByRole('region', { name: myAccount.personalTitle }));
    expect(await personal.findByText('3424000000')).toBeInTheDocument();
    expect(personal.getByText(myAccount.phoneSaved)).toHaveFocus();
    expect(personal.getByRole('button', { name: myAccount.editPhoneLabel })).toBeInTheDocument();
  });

  it('given a customer without a phone, when "Datos y acceso" opens, then "Agregar teléfono" is a row with its icon and chevron that opens the field (T-E7-63)', async () => {
    stubAccount();
    renderRoute(DATA_PATH);

    const add = await screen.findByRole('button', { name: myAccount.addPhoneLabel });
    expect(add).toHaveClass('group-row', 'group-action');
    expect(add.querySelector('.group-icon svg')).not.toBeNull();
    expect(add.querySelector('.group-chevron')).not.toBeNull();

    await userEvent.click(add);

    expect(screen.getByLabelText(myAccount.phoneField)).toHaveFocus();
  });

  it('given a phone of 3 digits, when the customer saves it, then it is told why and stays on the field (UC-40)', async () => {
    stubAccount({ account: { ...ANA, phone: '3425551234' } });
    vi.spyOn(api, 'PUT').mockResolvedValue(answer(422, { problem: 'INVALID_PHONE' }));
    renderRoute(DATA_PATH);

    await userEvent.click(await screen.findByRole('button', { name: myAccount.editPhoneLabel }));
    const field = screen.getByLabelText(myAccount.phoneField);
    expect(field).toHaveValue('3425551234');
    await userEvent.clear(field);
    await userEvent.type(field, '123');
    await userEvent.click(screen.getByRole('button', { name: myAccount.savePhone }));

    expect(await screen.findByText(myAccount.invalidPhone)).toBeInTheDocument();
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field).toHaveFocus();
    await userEvent.click(screen.getByRole('button', { name: myAccount.cancel }));
    expect(screen.getByText('3425551234')).toBeInTheDocument();
    // The focus goes back to where the edit started, never to <body>.
    expect(screen.getByRole('button', { name: myAccount.editPhoneLabel })).toHaveFocus();
  });

  it('given the phone field open, when Escape is pressed, then the edit is cancelled and the focus is back on "Editar teléfono" (UC-40)', async () => {
    stubAccount({ account: { ...ANA, phone: '3425551234' } });
    const put = vi.spyOn(api, 'PUT');
    renderRoute(DATA_PATH);

    await userEvent.click(await screen.findByRole('button', { name: myAccount.editPhoneLabel }));
    await userEvent.type(screen.getByLabelText(myAccount.phoneField), '9{Escape}');

    expect(put).not.toHaveBeenCalled();
    expect(screen.queryByLabelText(myAccount.phoneField)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: myAccount.editPhoneLabel })).toHaveFocus();
  });

  it('given the request fails, when the customer saves its phone, then it is told and stays on the field (UC-40)', async () => {
    stubAccount();
    vi.spyOn(api, 'PUT').mockResolvedValue(answer(500));
    renderRoute(DATA_PATH);

    await userEvent.click(await screen.findByRole('button', { name: myAccount.addPhoneLabel }));
    const field = screen.getByLabelText(myAccount.phoneField);
    await userEvent.type(field, '3424000000');
    await userEvent.click(screen.getByRole('button', { name: myAccount.savePhone }));

    expect(await screen.findByRole('alert')).toHaveTextContent(myAccount.phoneError);
    expect(field).toHaveFocus();
    expect(field).not.toHaveAttribute('aria-invalid');
  });

  it('given a save on its way, when it waits, then "Cancelar" cannot be pressed (UC-40)', async () => {
    stubAccount();
    vi.spyOn(api, 'PUT').mockReturnValue(new Promise<never>(() => undefined));
    renderRoute(DATA_PATH);

    await userEvent.click(await screen.findByRole('button', { name: myAccount.addPhoneLabel }));
    await userEvent.type(screen.getByLabelText(myAccount.phoneField), '3424000000');
    await userEvent.click(screen.getByRole('button', { name: myAccount.savePhone }));

    expect(await screen.findByRole('button', { name: myAccount.savingPhone })).toBeDisabled();
    expect(screen.getByRole('button', { name: myAccount.cancel })).toBeDisabled();
  });

  it('given no provider enabled nor linked, when the page opens, then "Formas de ingresar" has no footnote (T-E7-63)', async () => {
    stubAccount();
    renderRoute(DATA_PATH);

    const methods = await screen.findByRole('region', { name: myAccount.methodsTitle });
    expect(await within(methods).findByText(myAccount.passwordSet)).toBeInTheDocument();
    expect(methods.querySelector('.group-footnote')).toBeNull();
  });

  it('given the page, when it opens, then its personal data come first and "Privacidad" is folded, its delete a red row without a chevron', async () => {
    stubAccount();
    renderRoute(DATA_PATH);

    const personal = within(await screen.findByRole('region', { name: myAccount.personalTitle }));
    expect(personal.getByText('Ana Pérez')).toBeInTheDocument();
    const privacy = await screen.findByRole('button', { name: myAccount.privacyTitle });
    expect(privacy).toHaveAttribute('aria-expanded', 'false');
    // In the page's outline while closed.
    expect(screen.getByRole('heading', { level: 2, name: myAccount.privacyTitle })).toContainElement(privacy);

    await userEvent.click(privacy);

    const remove = screen.getByRole('button', { name: myAccount.deleteAccount });
    expect(remove).not.toHaveClass('btn');
    expect(remove).toHaveClass('group-destructive');
    expect(remove.querySelector('.group-chevron')).toBeNull();
    await userEvent.click(remove);
    expect(screen.getByText(myAccount.deleteOrders)).toBeInTheDocument();
  });
});
