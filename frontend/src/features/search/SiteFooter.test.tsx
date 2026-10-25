import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

  it('given no contact or social links from the business yet (D-15), when the footer is checked, then there is no Contacto y redes column', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(screen.queryByRole('region', { name: messages.social })).not.toBeInTheDocument();
  });

  it('given only the Instagram link, when the footer is checked, then Contacto y redes links Instagram in a new tab and neither TikTok nor Contacto', async () => {
    setStoreContact({ instagramUrl: 'https://www.instagram.com/pijamaparty/' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    const instagram = column(messages.social).getByRole('link', { name: messages.instagram });
    expect(instagram).toHaveAttribute('href', 'https://www.instagram.com/pijamaparty/');
    expect(instagram).toHaveAttribute('target', '_blank');
    expect(instagram).toHaveAttribute('rel', 'noopener noreferrer');
    expect(column(messages.social).queryByRole('link', { name: messages.tiktok })).not.toBeInTheDocument();
    expect(column(messages.social).queryByRole('link', { name: messages.contact })).not.toBeInTheDocument();
  });

  it('given a contact email, when "Contacto" is followed from another page, then the home page opens at "Contactanos"', async () => {
    setStoreContact({ email: 'hola@example.com' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    const scrollIntoView = vi.spyOn(Element.prototype, 'scrollIntoView');

    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    const contact = column(messages.social).getByRole('link', { name: messages.contact });
    expect(contact).toHaveAttribute('href', '/#contactanos');
    await userEvent.click(contact);
    const section = await screen.findByRole('region', { name: es.contact.title });
    await waitFor(() => {
      expect(scrollIntoView.mock.contexts).toContain(section);
    });
  });
});
