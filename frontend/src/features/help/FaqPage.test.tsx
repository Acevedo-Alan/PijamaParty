import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import {
  answer,
  MERCADOPAGO_ONLY,
  NO_CART,
  NO_SHIPPING_RATES,
  PAYMENT_PROVIDERS_PATH,
  SHIPPING_RATES_PATH,
  withHeader,
} from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = es.faq;

/** An amount as Testing Library reads it: the non-breaking space after the sign becomes a plain one. */
const shown = (cents: number) => formatMoney(cents).replace(/\s/g, ' ');

/** The FAQ's reads; the shipping rates answer in order, the last one repeats. */
function stubPage(providers: unknown, ...rates: unknown[]) {
  const queue = [...rates];
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) => {
    if (path === SHIPPING_RATES_PATH) {
      return Promise.resolve(queue.length > 1 ? queue.shift() : queue[0]);
    }
    if (path === PAYMENT_PROVIDERS_PATH) {
      return Promise.resolve(providers);
    }
    return Promise.resolve(path === '/api/cart' ? NO_CART : undefined);
  }));
}

function rates(zones: { province: string; costCents: number }[], freeShippingThresholdCents: number | null = null) {
  return answer(200, { zones, freeShippingThresholdCents });
}

async function shippingTable() {
  return within(await screen.findByRole('region', { name: messages.shipping.table }));
}

describe('the FAQ page (RNF-31.6, T-E5-12)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given costs for Santa Fe and Córdoba only, when the page opens, then the table lists just those two', async () => {
    stubPage(MERCADOPAGO_ONLY, rates([
      { province: 'CORDOBA', costCents: 0 },
      { province: 'SANTA_FE', costCents: 350_000 },
    ]));

    renderRoute('/preguntas-frecuentes');

    const table = await shippingTable();
    const rows = table.getAllByRole('row').slice(1).map((row) => row.textContent);
    expect(rows).toEqual([
      `${es.provinces.CORDOBA}${messages.shipping.free}`,
      `${es.provinces.SANTA_FE}${formatMoney(350_000)}`,
    ]);
    expect(screen.getByRole('heading', { level: 1, name: messages.title })).toBeInTheDocument();
    expect(screen.getByText(messages.shipping.notListed)).toBeInTheDocument();
  });

  it('given free shipping from $50.000, when the page opens, then it says so; without a threshold it says nothing', async () => {
    stubPage(MERCADOPAGO_ONLY, rates([{ province: 'SANTA_FE', costCents: 350_000 }], 5_000_000));
    const { unmount } = renderRoute('/preguntas-frecuentes');

    expect(await screen.findByText(messages.shipping.freeFrom(shown(5_000_000)))).toBeInTheDocument();
    unmount();
    vi.restoreAllMocks();

    stubPage(MERCADOPAGO_ONLY, rates([{ province: 'SANTA_FE', costCents: 350_000 }]));
    renderRoute('/preguntas-frecuentes');

    await shippingTable();
    expect(screen.queryByText(/gratis en compras/)).not.toBeInTheDocument();
  });

  it('given no province with shipping, when the page opens, then it says there is no shipping yet, without a table', async () => {
    stubPage(MERCADOPAGO_ONLY, NO_SHIPPING_RATES);

    renderRoute('/preguntas-frecuentes');

    expect(await screen.findByText(messages.shipping.none)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: messages.shipping.table })).not.toBeInTheDocument();
  });

  it.each([
    [['MERCADOPAGO', 'MODO'], [es.checkout.providers.MERCADOPAGO.name, es.checkout.providers.MODO.name]],
    [['MERCADOPAGO'], [es.checkout.providers.MERCADOPAGO.name]],
  ])('given the providers %j, when the page opens, then it lists their names', async (providers, names) => {
    stubPage(answer(200, { providers }), NO_SHIPPING_RATES);

    renderRoute('/preguntas-frecuentes');

    await userEvent.click(screen.getByText(messages.payment.question));
    const list = within(await screen.findByRole('list'));
    expect(list.getAllByRole('listitem').map((item) => item.querySelector('strong')?.textContent)).toEqual(names);
  });

  it('given the costs fail to load, when the page opens, then the fixed answers stay and a retry loads them', async () => {
    stubPage(MERCADOPAGO_ONLY, answer(500), rates([{ province: 'SANTA_FE', costCents: 350_000 }]));

    renderRoute('/preguntas-frecuentes');

    expect(await screen.findByText(messages.shipping.loadError)).toBeInTheDocument();
    expect(screen.getByText(messages.delivery.answer)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: messages.retry }));
    expect((await shippingTable()).getByText(es.provinces.SANTA_FE)).toBeInTheDocument();
  });

  it('given the returns question, when it opens, then it links the withdrawal button and the full policy', async () => {
    stubPage(MERCADOPAGO_ONLY, NO_SHIPPING_RATES);

    renderRoute('/preguntas-frecuentes');

    await userEvent.click(screen.getByText(messages.returns.question));
    expect(screen.getByRole('link', { name: messages.returns.policyLink }))
      .toHaveAttribute('href', '/cambios-y-devoluciones');
    const main = within(screen.getByRole('main'));
    expect(main.getByRole('link', { name: es.withdrawal.link })).toHaveAttribute('href', '/arrepentimiento');
  });

  it('given any public page, when it opens, then its footer links the FAQ', async () => {
    stubPage(MERCADOPAGO_ONLY, NO_SHIPPING_RATES);

    renderRoute('/arrepentimiento');

    const footer = within(await screen.findByRole('contentinfo'));
    expect(footer.getByRole('link', { name: messages.title })).toHaveAttribute('href', '/preguntas-frecuentes');
  });
});
