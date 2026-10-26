import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { answer, listed, product, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { clearStoreContact, setStoreContact } from '../../test/storeContact.ts';

const messages = es.footer;

const footer = () => within(screen.getByRole('contentinfo'));

const column = (name: string) => within(footer().getByRole('region', { name }));

describe('store footer (UC-31)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    clearStoreContact();
  });

  it('given any public page, when the footer is checked, then Ayuda links the FAQ and the withdrawal link is visible without signing in', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(column(messages.help).getByRole('link', { name: es.faq.title })).toHaveAttribute('href', '/preguntas-frecuentes');
    expect(footer().getByRole('link', { name: es.withdrawal.link })).toHaveAttribute('href', '/arrepentimiento');
    expect(footer().getByText(messages.copyright(new Date().getFullYear(), es.store.name))).toBeInTheDocument();
  });

  it('given any public page, when the footer is checked, then the logo links to the home page above the tagline and Tienda has the header navigation', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    const logo = footer().getByRole('link', { name: es.store.name });
    expect(logo).toHaveAttribute('href', '/');
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

  it('given the exchanges and terms pages not built yet (T-E6-06), when the footer is checked, then Ayuda does not link them', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(column(messages.help).getAllByRole('link')).toHaveLength(1);
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
      expect(item.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    });
  });

  it('given the payment providers failing, when the footer is checked, then there is no Medios de pago column', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])], paymentProviders: answer(500) });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    await waitFor(() => {
      expect(get).toHaveBeenCalledWith('/api/checkout/payment-providers', expect.anything());
    });
    expect(screen.queryByRole('region', { name: messages.payments })).not.toBeInTheDocument();
  });

  it('given no contact or social links from the business yet (D-15), when the footer is checked, then there are no Contacto and Redes sociales columns', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(screen.queryByRole('region', { name: messages.contact })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: messages.social })).not.toBeInTheDocument();
  });

  it('given a contact email and WhatsApp number, when the footer is checked, then Contacto links the chat in a new tab and a new email', async () => {
    setStoreContact({ email: 'hola@example.com', whatsAppNumber: '5493424000000' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    const whatsApp = column(messages.contact).getByRole('link', { name: '+54 9 3424000000' });
    expect(whatsApp).toHaveAttribute('href', 'https://wa.me/5493424000000');
    expect(whatsApp).toHaveAttribute('target', '_blank');
    expect(whatsApp).toHaveAttribute('rel', 'noopener noreferrer');
    expect(column(messages.contact).getByRole('link', { name: 'hola@example.com' })).toHaveAttribute('href', 'mailto:hola@example.com');
    expect(screen.queryByRole('region', { name: messages.social })).not.toBeInTheDocument();
  });

  it('given Instagram and TikTok links, when the footer is checked, then Redes sociales shows both as icons named after them, opening in a new tab', async () => {
    setStoreContact({ instagramUrl: 'https://www.instagram.com/pijamaparty/', tiktokUrl: 'https://www.tiktok.com/@pijamaparty' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
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
});
