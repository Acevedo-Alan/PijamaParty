import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { answer, listed, product, productDetail, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { resetAnalyticsForTests } from './analytics.ts';
import { mayBePersonal } from './catalogEvents.ts';
import { saveConsent } from './consent.ts';

/** The GA4 events sent so far, without the setup commands, as [name, params]. */
function gaEvents(name: string): unknown[] {
  return (window.dataLayer ?? [])
    .map((entry) => [...(entry as IArguments)] as unknown[])
    .filter((command) => command[0] === 'event' && command[1] === name)
    .map((command) => command[2]);
}

function pixelEvents(name: string): unknown[] {
  return (window.fbq?.queue ?? []).filter((call) => call[0] === 'track' && call[1] === name).map((call) => call[2]);
}

describe('catalog analytics events (RNF-25, T-E1-24)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_GA4_MEASUREMENT_ID', 'G-ABC123');
    vi.stubEnv('VITE_META_PIXEL_ID', '1234567890');
    saveConsent('accepted');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
    resetAnalyticsForTests();
    localStorage.clear();
    document.head.querySelectorAll('script').forEach((script) => {
      script.remove();
    });
  });

  it('given a category, when its products show and more are loaded, then each page is one view_item_list', async () => {
    stubCatalogApi({
      listings: [
        listed([product('luna', { fromPriceCents: 2_500_050 }), product('sol')], true),
        listed([product('mar')]),
      ],
    });
    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama luna/ });
    await userEvent.click(screen.getByRole('button', { name: es.catalog.showMore }));
    await screen.findByRole('link', { name: /Pijama mar/ });

    await waitFor(() => {
      expect(gaEvents('view_item_list')).toHaveLength(2);
    });
    expect(gaEvents('view_item_list')).toEqual([
      {
        item_list_id: 'category_verano',
        item_list_name: 'category_verano',
        items: [
          { item_id: 'luna', item_name: 'Pijama luna', price: 25000.5, index: 0 },
          { item_id: 'sol', item_name: 'Pijama sol', price: 12345, index: 1 },
        ],
      },
      {
        item_list_id: 'category_verano',
        item_list_name: 'category_verano',
        items: [{ item_id: 'mar', item_name: 'Pijama mar', price: 12345, index: 2 }],
      },
    ]);
  });

  it('given a product page, when it opens, then view_item and ViewContent go once in ARS', async () => {
    vi.spyOn((await import('../../api/client.ts')).api, 'GET').mockResolvedValue(answer(200, productDetail()));
    renderRoute('/producto/luna');

    await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' });

    await waitFor(() => {
      expect(gaEvents('view_item')).toEqual([
        { currency: 'ARS', value: 25000, items: [{ item_id: 'luna', item_name: 'Pijama Luna', price: 25000 }] },
      ]);
    });
    expect(pixelEvents('ViewContent')).toEqual([
      { content_ids: ['luna'], content_type: 'product', value: 25000, currency: 'ARS' },
    ]);
  });

  it('given a search, when its results open, then search and Search go with the text and the list is the results', async () => {
    stubCatalogApi({ listings: [listed([product('luna')])] });
    renderRoute('/buscar?q=pijama+rosa');

    await screen.findByRole('link', { name: /Pijama luna/ });

    await waitFor(() => {
      expect(gaEvents('view_item_list')).toHaveLength(1);
    });
    expect(gaEvents('search')).toEqual([{ search_term: 'pijama rosa' }]);
    expect(pixelEvents('Search')).toEqual([{ search_string: 'pijama rosa' }]);
    expect(gaEvents('view_item_list')[0]).toMatchObject({ item_list_id: 'search_results' });
  });

  it('given a text that may be an email or a national ID, when searched, then it is not sent', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar?q=ana%40example.com');
    await screen.findByText(es.search.noResults('ana@example.com'));

    expect(gaEvents('search')).toEqual([]);
    expect(mayBePersonal('30.123.456')).toBe(true);
    expect(mayBePersonal('pijama talle 12')).toBe(false);
  });

  it('given no consent, when products show, then no event is sent', async () => {
    localStorage.clear();
    stubCatalogApi({ listings: [listed([product('luna')])] });
    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama luna/ });

    expect(window.dataLayer).toBeUndefined();
    expect(window.fbq).toBeUndefined();
  });
});
