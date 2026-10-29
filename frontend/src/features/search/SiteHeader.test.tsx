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
  shopCategories,
  stubCatalogApi,
  withHeader,
} from '../../test/catalogApi.ts';
import { cart, cartItem } from '../../test/cartApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { clearStoreContact, setStoreContact } from '../../test/storeContact.ts';

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

/** The ticker's messages as screen readers get them: its first copy only, the second is aria-hidden. */
function tickerMessages() {
  return within(screen.getByRole('region', { name: es.header.ticker.label }))
    .getAllByRole('listitem')
    .map((item) => item.textContent.replace(/\s/g, ' '));
}

describe('announcement ticker (UC-31, T-E7-39)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a free-shipping threshold of $50,000 and MercadoPago, when any public page opens, then the ticker lists them with the carrier', async () => {
    stubCatalogApi({
      listings: [listed([])],
      shippingRates: answer(200, { zones: [], freeShippingThresholdCents: 5_000_000 }),
    });
    const { router } = renderRoute('/');

    await waitFor(() => {
      expect(tickerMessages()).toEqual([
        es.header.freeShippingFrom(shown(5_000_000)),
        es.header.ticker.carrier,
        es.faq.summary.payWith('MercadoPago'),
      ]);
    });
    expect(tickerMessages()[0]).toMatch(/^Envío gratis desde \$ 50\.000$/);

    await router.navigate('/categoria/verano');
    expect(tickerMessages()[0]).toBe(es.header.freeShippingFrom(shown(5_000_000)));
  });

  it.each([
    ['no threshold', answer(200, { zones: [], freeShippingThresholdCents: null })],
    ['the rates failing', answer(500)],
  ])('given %s, when a page opens, then the ticker leaves out free shipping', async (_, shippingRates) => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])], shippingRates });
    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    await waitFor(() => {
      expect(get).toHaveBeenCalledWith('/api/shipping/rates', expect.anything());
    });
    await waitFor(() => {
      expect(tickerMessages()).toEqual([es.header.ticker.carrier, es.faq.summary.payWith('MercadoPago')]);
    });
  });

  it('given the ticker running, when its pause button is pressed, then it stops; pressed again, it runs', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');
    const ticker = screen.getByRole('region', { name: es.header.ticker.label });

    await userEvent.click(within(ticker).getByRole('button', { name: es.header.ticker.pause }));

    expect(ticker).toHaveAttribute('data-paused');
    await userEvent.click(within(ticker).getByRole('button', { name: es.header.ticker.resume }));
    expect(ticker).not.toHaveAttribute('data-paused');
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

    const logo = within(screen.getByRole('banner')).getByRole('link', { name: es.store.name });
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
    vi.unstubAllGlobals();
    clearStoreContact();
  });

  it('given a visitor, when the menu opens, then it shows the navigation, and the account is "Ingresar" without "Crear cuenta" (T-E7-39)', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');

    await userEvent.click(await menuButton());

    expect(
      within(menu().getByRole('navigation', { name: es.header.navigation }))
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Todo', 'Mujer', 'Hombre', 'Niños', 'Temporada Invierno']);
    const shortcuts = within(await menu().findByRole('region', { name: es.header.shortcuts }));
    expect(shortcuts.getAllByRole('link')[0]).toHaveTextContent(es.account.signInLink);
    expect(shortcuts.getByRole('link', { name: es.account.signInLink })).toHaveAttribute('href', '/cuenta/ingresar');
    expect(menu().queryByRole('link', { name: es.account.registerLink })).not.toBeInTheDocument();
  });

  it('given the open menu, when it shows, then it is the panel from the left with the close button above the links (T-E7-39)', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');

    await userEvent.click(await menuButton());

    expect(screen.getByRole('dialog', { name: es.header.menuTitle })).toHaveClass('menu-panel');
    const close = menu().getByRole('button', { name: es.header.closeMenu });
    const navigation = menu().getByRole('navigation', { name: es.header.navigation });
    expect(close.compareDocumentPosition(navigation) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
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

  it('given a signed-in customer, when "Mi cuenta" is chosen in the menu, then its level lists the greeting and the account links (T-E7-39)', async () => {
    renderSignedIn();
    await userEvent.click(await menuButton());

    await userEvent.click(await menu().findByRole('button', { name: es.account.myAccountLink }));

    expect(menu().getByRole('button', { name: es.header.backLabel })).toHaveFocus();
    const level = menu().getByText(es.account.greeting('Ana')).closest('.menu-level') as HTMLElement;
    expect(level).not.toHaveAttribute('inert');
    expect(within(level).getAllByRole('link').map((link) => link.textContent)).toEqual([
      es.account.ordersLink,
      es.account.addressesLink,
      es.account.myAccountLink,
    ]);
    expect(within(level).getByRole('button', { name: es.account.signOut })).toBeInTheDocument();
    expect(menu().queryByRole('link', { name: es.account.signInLink })).not.toBeInTheDocument();
  });

  it('given active categories, when "Todo" is chosen, then its level shows them and "Volver" comes back to "Todo" (T-E7-37, T-E7-39)', async () => {
    stubCatalogApi({
      listings: [listed([])],
      categories: shopCategories([
        { slug: 'pijamas', name: 'Pijamas' },
        { slug: 'camisones', name: 'Camisones' },
      ]),
    });
    renderRoute('/buscar');
    await userEvent.click(await menuButton());
    const all = await menu().findByRole('button', { name: es.header.all });
    const first = all.closest('.menu-level') as HTMLElement;
    // Out of the accessibility tree while inert, so found by its text.
    const level = menu().getByText(es.header.seeAll).closest('.menu-level') as HTMLElement;
    expect(level).toHaveAttribute('inert');
    expect(menu().queryByRole('link', { name: es.header.seeAll })).not.toBeInTheDocument();

    await userEvent.click(all);

    expect(level).not.toHaveAttribute('inert');
    expect(first).toHaveAttribute('inert');
    expect(menu().getByRole('button', { name: es.header.backLabel })).toHaveFocus();
    expect(
      within(level)
        .getAllByRole('link')
        .map((link) => [link.textContent, link.getAttribute('href')]),
    ).toEqual([
      [es.header.seeAll, '/'],
      ['Pijamas', '/categoria/pijamas'],
      ['Camisones', '/categoria/camisones'],
    ]);

    await userEvent.click(menu().getByRole('button', { name: es.header.backLabel }));

    expect(level).toHaveAttribute('inert');
    expect(first).not.toHaveAttribute('inert');
    expect(all).toHaveFocus();
  });

  it('given no categories, when the menu opens, then "Todo" is a link to the home page (T-E7-37, T-E7-39)', async () => {
    const get = stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');
    await userEvent.click(await menuButton());

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith('/api/catalog/categories', expect.anything());
    });
    expect(menu().queryByRole('button', { name: es.header.all })).not.toBeInTheDocument();
    expect(menu().getByRole('link', { name: es.header.all })).toHaveAttribute('href', '/');
  });

  it('given a category page, when the "Todo" level opens, then that category is current (T-E7-37, T-E7-39)', async () => {
    stubCatalogApi({
      listings: [listed([])],
      categories: shopCategories([
        { slug: 'pijamas', name: 'Pijamas' },
        { slug: 'camisones', name: 'Camisones' },
      ]),
    });
    renderRoute('/categoria/camisones');
    await userEvent.click(await menuButton());

    await userEvent.click(await menu().findByRole('button', { name: es.header.all }));

    expect(menu().getByRole('link', { current: 'page' })).toHaveTextContent('Camisones');
  });

  it('given 2 units in the cart, when "Mi carrito" is chosen in the menu, then the menu closes and the cart panel opens (T-E7-37)', async () => {
    stubCatalogApi({ listings: [listed([])], cart: cart([cartItem({ quantity: 2 })]) });
    renderRoute('/buscar');
    await userEvent.click(await menuButton());

    await userEvent.click(await menu().findByRole('button', { name: es.header.cartShortcut(2) }));

    expect(screen.queryByRole('dialog', { name: es.header.menuTitle })).not.toBeInTheDocument();
    expect(await screen.findByRole('dialog', { name: es.cart.title })).toBeInTheDocument();
  });

  it('given the open menu, when its shortcuts are read, then "Guía de talles" opens the size guides', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');
    await userEvent.click(await menuButton());

    const shortcuts = within(menu().getByRole('region', { name: es.header.shortcuts }));
    expect(shortcuts.getByRole('link', { name: es.footer.sizeGuides })).toHaveAttribute('href', '/guia-de-talles');
  });

  it.each([
    ['the WhatsApp number', { whatsAppNumber: '5493424000000' }, [['Preguntas frecuentes', '/preguntas-frecuentes'], ['Escribinos por WhatsApp', 'https://wa.me/5493424000000']]],
    ['no number', {}, [['Preguntas frecuentes', '/preguntas-frecuentes']]],
  ])('given %s, when the menu opens, then its help links the FAQ and WhatsApp only with the number (T-E7-37)', async (_, contact, links) => {
    setStoreContact(contact);
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');
    await userEvent.click(await menuButton());

    const help = within(menu().getByRole('region', { name: es.header.help }));
    expect(help.getAllByRole('link').map((link) => [link.textContent, link.getAttribute('href')])).toEqual(links);
  });
});

describe('header on scroll (T-E7-15, T-E7-16, UC-31)', () => {
  afterEach(() => {
    scrollPageTo(0);
    vi.restoreAllMocks();
  });

  /** jsdom does not scroll: the position is set and the event sent, as the browser does. */
  function scrollPageTo(y: number) {
    Object.defineProperty(window, 'scrollY', { value: y, configurable: true });
    fireEvent.scroll(window);
  }

  function header() {
    return screen.getByRole('banner');
  }

  it('given a page, when the visitor scrolls down, then the header hides; when they scroll up, then it shows', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');
    await menuButton();

    scrollPageTo(300);
    await waitFor(() => {
      expect(header()).toHaveAttribute('data-hidden', 'true');
    });

    scrollPageTo(200);
    await waitFor(() => {
      expect(header()).not.toHaveAttribute('data-hidden');
    });
  });

  it('given a scrolled page, when the header shows, then it is translucent; back at the top, it is not (T-E7-16)', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');
    await menuButton();
    expect(header()).not.toHaveAttribute('data-scrolled');

    scrollPageTo(300);
    await waitFor(() => {
      expect(header()).toHaveAttribute('data-hidden', 'true');
    });
    scrollPageTo(250);
    await waitFor(() => {
      expect(header()).not.toHaveAttribute('data-hidden');
    });
    expect(header()).toHaveAttribute('data-scrolled', 'true');

    scrollPageTo(0);
    await waitFor(() => {
      expect(header()).not.toHaveAttribute('data-scrolled');
    });
  });

  it('given a hidden header, when the page moves up less than 8 px, then it stays hidden', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');
    await menuButton();
    scrollPageTo(300);
    await waitFor(() => {
      expect(header()).toHaveAttribute('data-hidden', 'true');
    });

    scrollPageTo(295);
    await new Promise((resolve) => requestAnimationFrame(resolve));

    expect(header()).toHaveAttribute('data-hidden', 'true');
  });

  it('given the open menu, when the page scrolls down, then the header stays', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');
    await userEvent.click(await menuButton());

    scrollPageTo(300);
    await new Promise((resolve) => requestAnimationFrame(resolve));

    // The menu has a header of its own; the page's is the one with the class.
    expect(document.querySelector('header.site')).not.toHaveAttribute('data-hidden');
  });
});

describe('header account entry (UC-31)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a visitor on a computer, when the header shows, then the account icon named "Ingresar" links to the sign-in page and there is no "Crear cuenta" (T-E7-23)', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar');

    const icon = await screen.findByRole('link', { name: es.account.signInLink });
    expect(icon).toHaveAttribute('href', '/cuenta/ingresar');
    expect(icon).toHaveClass('icon-btn');
    expect(icon).toHaveTextContent('');
    expect(screen.queryByRole('link', { name: es.account.registerLink })).not.toBeInTheDocument();
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

describe('header on the purchase pages (UC-31)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  const header = () => within(screen.getByRole('banner'));

  it.each(['/checkout', `/pedido/${'b'.repeat(43)}`])(
    'given %s, when the header shows, then it has the logo, the account and the cart, but no navigation, menu or search',
    async (path) => {
      vi.spyOn(api, 'GET').mockImplementation(withHeader((get: string) => Promise.resolve(headerRead(get) ?? answer(404))));
      renderRoute(path);

      expect(await header().findByRole('link', { name: es.account.signInLink })).toBeInTheDocument();
      expect(header().getByRole('link', { name: es.store.name })).toHaveAttribute('href', '/');
      expect(header().getByRole('button', { name: es.cart.open(0) })).toBeInTheDocument();
      expect(screen.queryByRole('navigation', { name: es.header.navigation })).not.toBeInTheDocument();
      expect(header().queryByLabelText(es.header.openMenu, { selector: 'button' })).not.toBeInTheDocument();
      expect(header().queryByRole('search')).not.toBeInTheDocument();
      expect(header().queryByRole('button', { name: es.search.open })).not.toBeInTheDocument();
    },
  );

  it('given a page outside the purchase, when the header shows, then it keeps the navigation and the search', async () => {
    vi.spyOn(api, 'GET').mockImplementation(withHeader((get: string) => Promise.resolve(headerRead(get) ?? purchaseInfo(get))));
    renderRoute('/preguntas-frecuentes');

    expect(await screen.findByRole('navigation', { name: es.header.navigation })).toBeInTheDocument();
    expect(header().getByRole('search')).toBeInTheDocument();
  });
});
