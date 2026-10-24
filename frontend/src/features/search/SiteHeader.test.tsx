import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import {
  answer,
  headerRead,
  listed,
  product,
  purchaseInfo,
  queriesTo,
  stubCatalogApi,
  withHeader,
} from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const PRODUCTS = '/api/catalog/products';
const ANA = { id: '00000000-0000-0000-0000-000000000001', email: 'ana@example.com', name: 'Ana Pérez' };

/** An amount as Testing Library reads it: the non-breaking space after the sign becomes a plain one. */
const shown = (cents: number) => formatMoney(cents).replace(/\s/g, ' ');

function headerNav() {
  return within(screen.getByRole('navigation', { name: es.header.navigation }));
}

/** The menu button shows below 860 px only; the base styles are the wide layout, which jsdom gets. */
function menuButton() {
  return screen.findByLabelText(es.header.openMenu, { selector: 'button' });
}

function menu() {
  return within(screen.getByRole('dialog', { name: es.header.menuTitle }));
}

/** The FAQ page, which reads nothing but the header's reads and the purchase summary, for a signed-in customer. */
function renderSignedIn() {
  vi.spyOn(api, 'GET').mockImplementation(
    withHeader((path: string) => Promise.resolve(headerRead(path) ?? purchaseInfo(path)), answer(200, ANA)),
  );
  return renderRoute('/preguntas-frecuentes');
}

describe('free-shipping strip (UC-31)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a free-shipping threshold of $50,000, when any public page opens, then the strip says so', async () => {
    stubCatalogApi({
      listings: [listed([])],
      shippingRates: answer(200, { zones: [], freeShippingThresholdCents: 5_000_000 }),
    });
    const { router } = renderRoute('/');

    const strip = await screen.findByText(es.header.freeShippingFrom(shown(5_000_000)));
    expect(strip).toHaveTextContent(/^Envío gratis desde \$\s50\.000$/);

    await router.navigate('/categoria/verano');
    expect(screen.getByText(es.header.freeShippingFrom(shown(5_000_000)))).toBeInTheDocument();
  });

  it.each([
    ['no threshold', answer(200, { zones: [], freeShippingThresholdCents: null })],
    ['the rates failing', answer(500)],
  ])('given %s, when a page opens, then there is no strip', async (_, shippingRates) => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])], shippingRates });
    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    await waitFor(() => {
      expect(get).toHaveBeenCalledWith('/api/shipping/rates', expect.anything());
    });
    expect(screen.queryByText(/Envío gratis desde/)).not.toBeInTheDocument();
  });
});

describe('store navigation (UC-31)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the header, when its links are read, then each opens the listing with its filter', () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');

    expect(
      headerNav()
        .getAllByRole('link')
        .map((link) => [link.textContent, link.getAttribute('href')]),
    ).toEqual([
      ['Todo', '/'],
      ['Mujer', '/?publico=mujer'],
      ['Hombre', '/?publico=hombre'],
      ['Niños', '/?publico=infantil'],
      ['Temporada Invierno', '/?temporada=invierno'],
    ]);
    expect(headerNav().queryByRole('link', { current: 'page' })).not.toBeInTheDocument();
  });

  it('given any public page, when the header shows, then the logo is an image named after the store that opens the home page', () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');

    const logo = screen.getByRole('link', { name: es.store.name });
    expect(logo).toHaveAttribute('href', '/');
    expect(within(logo).getByRole('img', { name: es.store.name })).toHaveAttribute('height', '48');
  });

  it.each([
    ['/', 'Todo'],
    ['/?publico=mujer&orden=menor-precio', 'Mujer'],
    ['/?temporada=invierno', 'Temporada Invierno'],
  ])('given the home page at %s, when the header shows, then "%s" is the current page', (path, current) => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute(path);

    expect(headerNav().getByRole('link', { current: 'page' })).toHaveTextContent(current);
  });

  it('given "Temporada Invierno", when chosen, then the grid asks for WINTER products only', async () => {
    const get = stubCatalogApi({ listings: [listed([product('polar')])] });
    const { router } = renderRoute('/buscar');

    await userEvent.click(headerNav().getByRole('link', { name: es.header.winter }));

    expect(router.state.location.search).toBe('?temporada=invierno');
    expect(await screen.findByRole('link', { name: /Pijama polar/ })).toBeInTheDocument();
    expect(queriesTo(get, PRODUCTS)).toEqual([{ season: ['WINTER'], page: 0 }]);
  });

  it('given the home page open, when a navigation link is chosen, then it goes to "Todos los productos" again', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    const scroll = vi.spyOn(Element.prototype, 'scrollIntoView');
    renderRoute('/');
    await screen.findByRole('link', { name: /Pijama rosa/ });
    const heading = screen.getByRole('heading', { name: es.catalog.allTitle });

    await userEvent.click(headerNav().getByRole('link', { name: es.header.audiences.WOMEN }));

    expect(scroll.mock.contexts).toContain(heading);
    expect(heading).toHaveFocus();
  });
});

describe('phone menu (UC-31)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a visitor, when the menu opens, then it shows the navigation and the account links', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');

    await userEvent.click(await menuButton());

    expect(
      within(menu().getByRole('navigation', { name: es.header.navigation }))
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Todo', 'Mujer', 'Hombre', 'Niños', 'Temporada Invierno']);
    expect(menu().getByRole('link', { name: es.account.signInLink })).toHaveAttribute('href', '/cuenta/ingresar');
    expect(menu().getByRole('link', { name: es.account.registerLink })).toHaveAttribute('href', '/cuenta/registro');
  });

  it('given the open menu, when Escape is pressed, then it closes and the focus goes back to the menu button', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');
    const button = await menuButton();
    await userEvent.click(button);

    // jsdom has no Escape handling for dialogs: the browser sends a cancelable "cancel" event.
    fireEvent(screen.getByRole('dialog', { name: es.header.menuTitle }), new Event('cancel', { cancelable: true }));

    expect(screen.queryByRole('dialog', { name: es.header.menuTitle })).not.toBeInTheDocument();
    expect(button).toHaveFocus();
  });

  it('given the open menu, when a link is chosen, then its listing opens and the menu closes', async () => {
    stubCatalogApi({ listings: [listed([])] });
    const { router } = renderRoute('/buscar');
    await userEvent.click(await menuButton());

    await userEvent.click(menu().getByRole('link', { name: es.header.audiences.KIDS }));

    expect(router.state.location.search).toBe('?publico=infantil');
    expect(screen.queryByRole('dialog', { name: es.header.menuTitle })).not.toBeInTheDocument();
  });

  it('given a signed-in customer, when the menu opens, then it lists the greeting and the account links open', async () => {
    renderSignedIn();

    await userEvent.click(await menuButton());

    expect(await menu().findByText(es.account.greeting('Ana'))).toBeInTheDocument();
    expect(menu().getAllByRole('link').slice(-3).map((link) => link.textContent)).toEqual([
      es.account.ordersLink,
      es.account.addressesLink,
      es.account.myAccountLink,
    ]);
    expect(menu().getByRole('button', { name: es.account.signOut })).toBeInTheDocument();
  });
});

describe('header account entry (UC-31)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a visitor, when the header shows, then it offers "Ingresar" and "Crear cuenta"', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');

    expect(await screen.findByRole('link', { name: es.account.signInLink })).toHaveAttribute('href', '/cuenta/ingresar');
    expect(screen.getByRole('link', { name: es.account.registerLink })).toHaveAttribute('href', '/cuenta/registro');
  });

  it('given a signed-in customer, when "Mi cuenta" is opened, then it lists the account links and choosing one closes it', async () => {
    renderSignedIn();
    const button = await screen.findByRole('button', { name: es.account.menu });
    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('link', { name: es.account.ordersLink })).not.toBeInTheDocument();

    await userEvent.click(button);

    expect(button).toHaveAttribute('aria-expanded', 'true');
    const list = within(screen.getByText(es.account.greeting('Ana')).parentElement as HTMLElement);
    expect(list.getAllByRole('link').map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
      [es.account.ordersLink, '/cuenta/pedidos'],
      [es.account.addressesLink, '/cuenta/direcciones'],
      [es.account.myAccountLink, '/cuenta'],
    ]);
    expect(list.getByRole('button', { name: es.account.signOut })).toBeInTheDocument();

    await userEvent.click(list.getByRole('link', { name: es.account.ordersLink }));
    expect(button).toHaveAttribute('aria-expanded', 'false');
  });

  it('given the open account list, when Escape is pressed or the page is clicked, then it closes', async () => {
    renderSignedIn();
    const button = await screen.findByRole('button', { name: es.account.menu });

    await userEvent.click(button);
    await userEvent.keyboard('{Escape}');

    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(button).toHaveFocus();

    await userEvent.click(button);
    await userEvent.click(screen.getByRole('main'));

    expect(button).toHaveAttribute('aria-expanded', 'false');
  });
});
