import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { answer, listed, product, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { clearStoreContact, setStoreContact } from '../../test/storeContact.ts';
import footerCss from './footer.css?raw';

const messages = es.footer;

const footer = () => within(screen.getByRole('contentinfo'));

const column = (name: string) => within(footer().getByRole('region', { name }));

describe('store footer (UC-31)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    clearStoreContact();
  });

  it('given any public page, when the footer is checked, then Ayuda links the FAQ and the withdrawal link without signing in', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama rosa/i });
    expect(column(messages.help).getByRole('link', { name: es.faq.title })).toHaveAttribute('href', '/preguntas-frecuentes');
    expect(column(messages.help).getByRole('link', { name: es.withdrawal.link })).toHaveAttribute('href', '/arrepentimiento');
    expect(footer().getByText(messages.copyright(new Date().getFullYear(), es.store.name))).toBeInTheDocument();
  });

  it('given any public page, when the footer shows, then its night strip has a moon and nine stars, hidden from screen readers (T-E7-54)', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama rosa/i });
    const strip = screen.getByRole('contentinfo').querySelector('.footer-night');
    expect(strip).toHaveAttribute('aria-hidden', 'true');
    expect(strip?.querySelector('svg.footer-moon')).not.toBeNull();
    expect(strip?.querySelectorAll('.footer-star')).toHaveLength(9);
    expect(strip).toHaveTextContent(/^$/);
  });

  it('given any public page, when the footer shows, then the moon holds the rabbit and the strip has two lucky clouds (T-E7-55)', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama rosa/i });
    const strip = screen.getByRole('contentinfo').querySelector('.footer-night');
    expect(strip?.querySelector('svg.footer-moon .footer-rabbit-eyes')).not.toBeNull();
    expect(strip?.querySelectorAll('svg.footer-cloud')).toHaveLength(2);
  });

  it('given the footer styles, when read, then the stars, clouds and rabbit move only without reduced motion (T-E7-54, T-E7-55)', () => {
    const block = /@media \(prefers-reduced-motion: no-preference\) \{\s*\.footer-star \{[\s\S]*?\n\}/.exec(footerCss)?.[0] ?? '';
    expect(block).toMatch(/\.footer-star \{\s*animation: footer-twinkle/);
    expect(block).toMatch(/\.footer-cloud \{\s*animation: footer-drift/);
    expect(block).toMatch(/\.footer-rabbit-eyes \{\s*animation: footer-blink/);
  });

  it('given any public page, when the footer is checked, then it shows the tagline without the store name in large letters, and Tienda has the header navigation', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama rosa/i });
    expect(footer().queryByRole('link', { name: es.store.name })).not.toBeInTheDocument();
    expect(footer().getByText(messages.tagline)).toBeInTheDocument();
    const links = column(messages.shop).getAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual([
      es.header.all,
      es.header.audiences.WOMEN,
      es.header.audiences.MEN,
      es.header.audiences.KIDS,
      es.header.winter,
    ]);
    const header = within(screen.getByRole('navigation', { name: es.header.navigation }));
    expect(links.map((link) => link.getAttribute('href'))).toEqual(
      header.getAllByRole('link').map((link) => link.getAttribute('href')),
    );
  });

  it('given MercadoPago and MODO enabled, when the footer is checked, then Medios de pago shows both icons in a row, named after them', async () => {
    stubCatalogApi({
      listings: [listed([product('rosa')])],
      paymentProviders: answer(200, { providers: ['MERCADOPAGO', 'MODO'] }),
    });

    renderRoute('/');

    const payments = within(await screen.findByRole('region', { name: messages.payments }));
    const items = payments.getAllByRole('listitem');
    expect(items.map((item) => item.textContent)).toEqual(['MercadoPago', 'MODO']);
    items.forEach((item) => {
      expect(item.querySelector('svg')).toBeNull();
    });
  });

  it('given the payment providers failing, when the footer is checked, then Medios de pago shows only Envíos', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])], paymentProviders: answer(500) });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/i });
    await waitFor(() => {
      expect(get).toHaveBeenCalledWith('/api/checkout/payment-providers', expect.anything());
    });
    expect(screen.queryByRole('region', { name: messages.payments })).not.toBeInTheDocument();
    const shipping = within(screen.getByRole('region', { name: messages.shipping }));
    expect(shipping.getByText(messages.carrier)).toBeInTheDocument();
  });

  it('given MercadoPago enabled, when the footer is checked, then Envíos names Correo Argentino as plain text', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])], paymentProviders: answer(200, { providers: ['MERCADOPAGO'] }) });

    renderRoute('/');

    await screen.findByRole('region', { name: messages.payments });
    const carrier = column(messages.shipping).getByRole('listitem');
    expect(carrier).toHaveTextContent(messages.carrier);
    expect(carrier.querySelector('svg')).toBeNull();
  });

  it('given opening hours and an address, when the footer is checked, then Contacto shows the hours and links the address to Google Maps in a new tab', async () => {
    setStoreContact({ openingHours: 'Lunes a viernes de 9 a 18 h', address: 'San Martín 2000, Santa Fe' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/i });
    expect(column(messages.contact).getByText('Lunes a viernes de 9 a 18 h')).toBeInTheDocument();
    const address = column(messages.contact).getByRole('link', { name: 'San Martín 2000, Santa Fe' });
    expect(address).toHaveAttribute('href', 'https://www.google.com/maps/search/?api=1&query=San%20Mart%C3%ADn%202000%2C%20Santa%20Fe');
    expect(address).toHaveAttribute('target', '_blank');
    expect(address).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('given no contact or social links from the business yet (D-15), when the footer is checked, then there is no Contacto column and no Redes sociales (T-E7-40)', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/i });
    expect(screen.queryByRole('region', { name: messages.contact })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: messages.social })).not.toBeInTheDocument();
  });

  it('given only the Instagram link, when the footer is checked, then Redes sociales shows only Instagram, with the blooming circle (T-E7-40)', async () => {
    setStoreContact({ instagramUrl: 'https://www.instagram.com/pijamaparty.zzz/' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/i });
    const links = column(messages.social).getAllByRole('link');
    expect(links.map((link) => [link.getAttribute('aria-label'), link.getAttribute('href')])).toEqual([
      [messages.instagram, 'https://www.instagram.com/pijamaparty.zzz/'],
    ]);
    expect(links[0]).toHaveClass('social-link');
  });

  it('given a contact email and WhatsApp number, when the footer is checked, then Contacto links the chat in a new tab and a new email', async () => {
    setStoreContact({ email: 'hola@example.com', whatsAppNumber: '5493424000000' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama rosa/i });
    const whatsApp = column(messages.contact).getByRole('link', { name: '+54 9 3424000000' });
    expect(whatsApp).toHaveAttribute('href', 'https://wa.me/5493424000000');
    expect(whatsApp).toHaveAttribute('target', '_blank');
    expect(whatsApp).toHaveAttribute('rel', 'noopener noreferrer');
    expect(column(messages.contact).getByRole('link', { name: 'hola@example.com' })).toHaveAttribute('href', 'mailto:hola@example.com');
  });

  it('given Instagram and TikTok links, when the footer is checked, then Redes sociales shows both as icons named after them, opening in a new tab', async () => {
    setStoreContact({ instagramUrl: 'https://www.instagram.com/pijamaparty/', tiktokUrl: 'https://www.tiktok.com/@pijamaparty' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/i });
    const links = column(messages.social).getAllByRole('link');
    expect(links.map((link) => link.getAttribute('aria-label'))).toEqual([messages.instagram, messages.tiktok]);
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      'https://www.instagram.com/pijamaparty/',
      'https://www.tiktok.com/@pijamaparty',
    ]);
    links.forEach((link) => {
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', 'noopener noreferrer');
      expect(link.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    });
    expect(screen.queryByRole('region', { name: messages.contact })).not.toBeInTheDocument();
  });

  it('given a Facebook link and no TikTok link in "Datos de la tienda", when the footer is checked, then Redes sociales links Facebook next to Instagram and has no TikTok (UC-36)', async () => {
    setStoreContact({
      instagramUrl: 'https://www.instagram.com/pijamaparty.zzz/',
      facebookUrl: 'https://www.facebook.com/pijamaparty',
    });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    const social = within(await screen.findByRole('region', { name: messages.social }));
    expect(social.getAllByRole('link').map((link) => [link.getAttribute('aria-label'), link.getAttribute('href')])).toEqual([
      [messages.instagram, 'https://www.instagram.com/pijamaparty.zzz/'],
      [messages.facebook, 'https://www.facebook.com/pijamaparty'],
    ]);
    expect(social.queryByRole('link', { name: messages.tiktok })).not.toBeInTheDocument();
  });
});
