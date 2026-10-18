import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import {
  answer,
  detailOrNoReviews,
  MERCADOPAGO_ONLY,
  NO_CART,
  NO_SHIPPING_RATES,
  PAYMENT_PROVIDERS_PATH,
  SHIPPING_RATES_PATH,
  withHeader,
} from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = es.faq.summary;

/** An amount as Testing Library reads it: the non-breaking space after the sign becomes a plain one. */
const shown = (cents: number) => formatMoney(cents).replace(/\s/g, ' ');

/** The product page, with the given shipping rates and payment providers. */
function stubPage(rates: unknown, providers: unknown = MERCADOPAGO_ONLY) {
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) => {
    if (path === SHIPPING_RATES_PATH) {
      return Promise.resolve(rates);
    }
    if (path === PAYMENT_PROVIDERS_PATH) {
      return Promise.resolve(providers);
    }
    return Promise.resolve(path === '/api/cart' ? NO_CART : detailOrNoReviews(path));
  }));
}

async function summary() {
  return within(await screen.findByRole('region', { name: messages.label }));
}

describe('the purchase summary of the product page (RNF-31.6, T-E5-12)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given free shipping and MODO switched on, when a product opens, then the summary shows both, the times and the link', async () => {
    stubPage(answer(200, { zones: [], freeShippingThresholdCents: 5_000_000 }),
      answer(200, { providers: ['MERCADOPAGO', 'MODO'] }));

    renderRoute('/producto/luna');

    const box = await summary();
    expect(await box.findByText(messages.freeFrom(shown(5_000_000)))).toBeInTheDocument();
    expect(box.getByText(messages.delivery)).toBeInTheDocument();
    expect(await box.findByText(messages.payWith('MercadoPago o MODO'))).toBeInTheDocument();
    expect(box.getByRole('link', { name: messages.link })).toHaveAttribute('href', '/preguntas-frecuentes');
  });

  it('given no free shipping, when a product opens, then the summary leaves the threshold out', async () => {
    stubPage(NO_SHIPPING_RATES);

    renderRoute('/producto/luna');

    const box = await summary();
    expect(await box.findByText(messages.payWith('MercadoPago'))).toBeInTheDocument();
    expect(box.getAllByRole('listitem')).toHaveLength(2);
    expect(box.queryByText(/Envío gratis/)).not.toBeInTheDocument();
  });

  it('given the costs and the providers fail to load, when a product opens, then the summary keeps the times and the link', async () => {
    stubPage(answer(500), answer(500));

    renderRoute('/producto/luna');

    const box = await summary();
    expect(box.getByText(messages.delivery)).toBeInTheDocument();
    expect(box.getByRole('link', { name: messages.link })).toBeInTheDocument();
    expect(box.getAllByRole('listitem')).toHaveLength(1);
  });
});
