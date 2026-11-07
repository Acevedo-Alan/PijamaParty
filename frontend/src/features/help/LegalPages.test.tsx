import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { listed, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const description = () => document.querySelector('meta[name="description"]')?.getAttribute('content');
const robots = () => document.querySelector('meta[name="robots"]');

describe('legal pages (RNF-30.5, RNF-25, T-E6-06)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllEnvs();
  });

  it('given /privacidad, when it opens, then it shows every section, names the processors and links the cookies and data deletion pages', async () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/privacidad');

    const main = within(await screen.findByRole('main'));
    expect(main.getByRole('heading', { level: 1, name: es.privacy.title })).toBeInTheDocument();
    expect(main.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)).toEqual(
      Object.values(es.privacy.sections).map((section) => section.title),
    );
    const sharing = main.getByText(es.privacy.sections.sharing.text).textContent;
    for (const processor of ['MercadoPago', 'MODO', 'Correo Argentino', 'Google', 'Meta']) {
      expect(sharing).toContain(processor);
    }
    expect(main.getByRole('link', { name: es.privacy.cookiesLink })).toHaveAttribute('href', '/cookies');
    expect(main.getByRole('link', { name: es.privacy.deletionLink })).toHaveAttribute('href', '/eliminacion-de-datos');
    expect(description()).toBe(es.seo.privacyDescription);
    expect(robots()).toBeNull();
  });

  it('given /cookies in a build with analytics, when it opens, then it lists the necessary and the optional cookies and the way to change the choice', async () => {
    vi.stubEnv('VITE_GA4_MEASUREMENT_ID', 'G-ABC123');
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/cookies');

    const main = within(await screen.findByRole('main'));
    expect(main.getByRole('heading', { level: 1, name: es.cookiesPolicy.title })).toBeInTheDocument();
    const necessary = within(main.getByRole('region', { name: es.cookiesPolicy.necessaryTitle }));
    expect(necessary.getAllByRole('listitem').map((cookie) => cookie.textContent)).toEqual(es.cookiesPolicy.necessary);
    const optional = within(main.getByRole('region', { name: es.cookiesPolicy.optionalTitle }));
    expect(optional.getAllByRole('listitem').map((cookie) => cookie.textContent)).toEqual(es.cookiesPolicy.optional);
    expect(main.getByRole('region', { name: es.cookiesPolicy.choiceTitle })).toBeInTheDocument();
    expect(main.getByRole('link', { name: es.cookiesPolicy.privacyLink })).toHaveAttribute('href', '/privacidad');
    expect(description()).toBe(es.seo.cookiesDescription);
    expect(robots()).toBeNull();
  });

  it('given /cookies in a build without analytics, when it opens, then it shows only the necessary cookies', async () => {
    vi.stubEnv('VITE_GA4_MEASUREMENT_ID', '');
    vi.stubEnv('VITE_META_PIXEL_ID', '');
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/cookies');

    const main = within(await screen.findByRole('main'));
    expect(main.getByRole('region', { name: es.cookiesPolicy.necessaryTitle })).toBeInTheDocument();
    expect(main.queryByRole('region', { name: es.cookiesPolicy.optionalTitle })).not.toBeInTheDocument();
    expect(main.queryByRole('region', { name: es.cookiesPolicy.choiceTitle })).not.toBeInTheDocument();
  });

  it('given /eliminacion-de-datos, when it opens, then it shows the steps, what is kept and links the privacy policy', async () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/eliminacion-de-datos');

    const main = within(await screen.findByRole('main'));
    expect(main.getByRole('heading', { level: 1, name: es.dataDeletion.title })).toBeInTheDocument();
    const steps = within(main.getByRole('region', { name: es.dataDeletion.stepsTitle }));
    expect(steps.getAllByRole('listitem').map((step) => step.textContent)).toEqual(es.dataDeletion.steps);
    expect(main.getByRole('region', { name: es.dataDeletion.withoutAccessTitle })).toBeInTheDocument();
    expect(main.getByText(es.dataDeletion.what)).toBeInTheDocument();
    expect(main.getByRole('link', { name: es.dataDeletion.privacyLink })).toHaveAttribute('href', '/privacidad');
    expect(description()).toBe(es.seo.dataDeletionDescription);
    expect(robots()).toBeNull();
  });
});
