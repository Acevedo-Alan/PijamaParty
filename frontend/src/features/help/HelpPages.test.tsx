import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { listed, product, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const description = () => document.querySelector('meta[name="description"]')?.getAttribute('content');

describe('help pages (UC-31, T-E7-28)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given any public page, when the footer is checked, then Ayuda y legales links the FAQ, Cómo comprar, Cambios y devoluciones and Términos', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    const help = within(within(screen.getByRole('contentinfo')).getByRole('region', { name: es.footer.help }));
    expect(help.getAllByRole('link').map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
      [es.faq.title, '/preguntas-frecuentes'],
      [es.footer.howToBuy, '/como-comprar'],
      [es.footer.sizeGuides, '/guia-de-talles'],
      [es.footer.returns, '/cambios-y-devoluciones'],
      [es.footer.terms, '/terminos'],
    ]);
  });

  it('given /como-comprar, when it opens, then it shows the five steps in order, its description and a link to the FAQ', async () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/como-comprar');

    const main = within(await screen.findByRole('main'));
    expect(main.getByRole('heading', { level: 1, name: es.howToBuy.title })).toBeInTheDocument();
    const steps = within(main.getByRole('list')).getAllByRole('listitem');
    expect(steps.map((step) => within(step).getByRole('heading').textContent)).toEqual(
      es.howToBuy.steps.map((step) => step.title),
    );
    expect(main.getByRole('link', { name: es.howToBuy.moreLink })).toHaveAttribute('href', '/preguntas-frecuentes');
    expect(description()).toBe(es.seo.howToBuyDescription);
  });

  it('given /cambios-y-devoluciones, when it opens, then it shows the exchanges policy and links the withdrawal page', async () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/cambios-y-devoluciones');

    const main = within(await screen.findByRole('main'));
    expect(main.getByRole('heading', { level: 1, name: es.returnsPolicy.title })).toBeInTheDocument();
    const exchanges = within(main.getByRole('region', { name: es.returnsPolicy.exchangesTitle }));
    expect(exchanges.getAllByRole('listitem').map((rule) => rule.textContent)).toEqual(es.returnsPolicy.exchanges);
    const withdrawal = within(main.getByRole('region', { name: es.returnsPolicy.withdrawalTitle }));
    expect(withdrawal.getByRole('link', { name: es.withdrawal.link })).toHaveAttribute('href', '/arrepentimiento');
    expect(description()).toBe(es.seo.returnsDescription);
  });

  it('given /terminos, when it opens, then it shows every section and links the exchanges policy and the withdrawal page', async () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/terminos');

    const main = within(await screen.findByRole('main'));
    expect(main.getByRole('heading', { level: 1, name: es.terms.title })).toBeInTheDocument();
    const { acceptance, products, purchase, shipping, data, law, contact } = es.terms.sections;
    expect(main.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)).toEqual([
      acceptance.title,
      products.title,
      purchase.title,
      shipping.title,
      es.terms.exchangesTitle,
      data.title,
      law.title,
      contact.title,
    ]);
    expect(main.getByRole('link', { name: es.terms.policyLink })).toHaveAttribute('href', '/cambios-y-devoluciones');
    expect(main.getByRole('link', { name: es.withdrawal.link })).toHaveAttribute('href', '/arrepentimiento');
    expect(description()).toBe(es.seo.termsDescription);
  });
});
