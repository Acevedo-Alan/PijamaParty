import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { answer, listed, product, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = es.benefits;

const bar = () => screen.getByRole('region', { name: messages.label });

const titles = () => within(bar()).getAllByRole('listitem').map((item) => item.querySelector('.benefit-title')?.textContent);

describe('benefits bar (UC-31, T-E7-21, T-E7-23)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given MercadoPago and MODO enabled, when the home page opens, then above the footer it shows shipping, the enabled payment methods and security', async () => {
    stubCatalogApi({
      listings: [listed([product('rosa')])],
      paymentProviders: answer(200, { providers: ['MERCADOPAGO', 'MODO'] }),
    });

    renderRoute('/');

    expect(await within(bar()).findByText(es.faq.summary.payWith('MercadoPago o MODO'))).toBeInTheDocument();
    expect(titles()).toEqual([messages.shippingTitle, messages.paymentsTitle, messages.securityTitle]);
    expect(within(bar()).getByText(messages.shippingText)).toBeInTheDocument();
    expect(within(bar()).getByText(messages.securityText)).toBeInTheDocument();
    expect(bar().compareDocumentPosition(screen.getByRole('contentinfo')) & Node.DOCUMENT_POSITION_FOLLOWING)
      .toBeTruthy();
  });

  it('given the payment providers failing, when the home page opens, then only the shipping and security blocks show', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])], paymentProviders: answer(500) });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    await waitFor(() => {
      expect(get).toHaveBeenCalledWith('/api/checkout/payment-providers', expect.anything());
    });
    expect(titles()).toEqual([messages.shippingTitle, messages.securityTitle]);
  });

  it('given any other page, such as the sign-in page, when it opens, then there is no benefits bar', async () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/cuenta/ingresar');

    expect(await screen.findByRole('heading', { level: 1, name: es.account.signIn.title })).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: messages.label })).not.toBeInTheDocument();
  });
});
