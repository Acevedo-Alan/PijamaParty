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

  it('given the exchanges and terms pages not built yet (T-E6-06), when the footer is checked, then Ayuda does not link them', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(column(messages.help).getAllByRole('link')).toHaveLength(1);
  });

  it('given MercadoPago and MODO enabled, when the footer is checked, then Medios de pago lists both', async () => {
    stubCatalogApi({
      listings: [listed([product('rosa')])],
      paymentProviders: answer(200, { providers: ['MERCADOPAGO', 'MODO'] }),
    });

    renderRoute('/');

    const payments = within(await screen.findByRole('region', { name: messages.payments }));
    expect(payments.getAllByRole('listitem').map((item) => item.textContent)).toEqual(['MercadoPago', 'MODO']);
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

  it('given no social links from the business yet (D-15), when the footer is checked, then there is no Redes column', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(screen.queryByRole('region', { name: messages.social })).not.toBeInTheDocument();
  });

  it('given only the Instagram link, when the footer is checked, then Redes links Instagram in a new tab and not TikTok', async () => {
    setStoreContact({ instagramUrl: 'https://www.instagram.com/pijamaparty/' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    const instagram = column(messages.social).getByRole('link', { name: messages.instagram });
    expect(instagram).toHaveAttribute('href', 'https://www.instagram.com/pijamaparty/');
    expect(instagram).toHaveAttribute('target', '_blank');
    expect(instagram).toHaveAttribute('rel', 'noopener noreferrer');
    expect(column(messages.social).queryByRole('link', { name: messages.tiktok })).not.toBeInTheDocument();
  });
});
