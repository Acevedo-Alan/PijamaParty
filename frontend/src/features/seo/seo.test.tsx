import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import {
  answer,
  detailVariant,
  listed,
  NO_CART,
  NO_REVIEWS,
  product,
  productDetail,
  purchaseInfo,
  REVIEWS_PATH,
  shopCategories,
  stubCatalogApi,
  withHeader,
  type DetailBody,
} from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { cutDescription } from './productMeta.ts';

/** The product page's reads: the product, its reviews, an empty cart and no other product of its category. */
function stubProduct(detail: DetailBody, reviews: unknown = NO_REVIEWS) {
  vi.spyOn(api, 'GET').mockImplementation(
    withHeader((path: string) => {
      if (path === '/api/cart') {
        return Promise.resolve(NO_CART);
      }
      if (path === '/api/catalog/products') {
        return Promise.resolve(listed([]));
      }
      if (path === REVIEWS_PATH) {
        return Promise.resolve(reviews);
      }
      return Promise.resolve(purchaseInfo(path) ?? answer(200, detail));
    }),
  );
}

interface StructuredData {
  '@type': string;
  name: string;
  description?: string;
  url: string;
  image?: string[];
  offers: { sku: string; price: string; priceCurrency: string; availability: string; url: string }[];
  aggregateRating?: { ratingValue: number; reviewCount: number };
}

function structuredData(): StructuredData {
  const scripts = document.querySelectorAll('script[type="application/ld+json"]');
  expect(scripts).toHaveLength(1);
  return JSON.parse(scripts[0]?.textContent ?? '') as StructuredData;
}

const robots = () => document.head.querySelector('meta[name="robots"]');
const description = () => document.head.querySelector('meta[name="description"]')?.getAttribute('content');
const canonical = () => document.head.querySelector('link[rel="canonical"]')?.getAttribute('href');

describe('search engines (T-E6-23)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a product with two prices, stock in one variant and reviews, when its page renders, then its structured data has an offer per variant and its rating', async () => {
    stubProduct(
      productDetail({
        description: 'Pijama largo\nde algodón.',
        fromPriceCents: 2_000_000,
        variants: [detailVariant('M', 'Rosa', 3, 2_000_000), detailVariant('L', 'Rosa', 0, 2_250_050)],
      }),
      answer(200, { average: 4.5, count: 2, reviews: [] }),
    );

    renderRoute('/producto/luna?color=Rosa');

    await waitFor(() => { expect(structuredData().aggregateRating).toBeDefined(); });
    const origin = window.location.origin;
    expect(structuredData()).toEqual({
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: 'Pijama Luna',
      description: 'Pijama largo de algodón.',
      url: `${origin}/producto/luna`,
      image: expect.arrayContaining([expect.stringMatching(new RegExp(`^${origin}/`))]) as unknown,
      brand: { '@type': 'Brand', name: es.store.name },
      offers: [
        {
          '@type': 'Offer',
          sku: 'PP-MROSA',
          price: '20000.00',
          priceCurrency: 'ARS',
          availability: 'https://schema.org/InStock',
          url: `${origin}/producto/luna`,
        },
        {
          '@type': 'Offer',
          sku: 'PP-LROSA',
          price: '22500.50',
          priceCurrency: 'ARS',
          availability: 'https://schema.org/OutOfStock',
          url: `${origin}/producto/luna`,
        },
      ],
      aggregateRating: { '@type': 'AggregateRating', ratingValue: 4.5, reviewCount: 2 },
    });
    expect(description()?.replace(/\s/g, ' ')).toBe('Desde $ 20.000. Pijama largo de algodón.');
    expect(canonical()).toBe(`${origin}/producto/luna`);
    expect(robots()).toBeNull();
  });

  it('given a product without reviews or photos, when its page renders, then there is no rating nor image', async () => {
    stubProduct(productDetail({ images: [] }));

    renderRoute('/producto/luna');

    await screen.findByRole('heading', { level: 1, name: 'Pijama Luna' });
    await waitFor(() => { expect(screen.queryByText(es.reviews.product.loading)).toBeNull(); });
    expect(structuredData().aggregateRating).toBeUndefined();
    expect(structuredData().image).toBeUndefined();
    expect(description()?.replace(/\s/g, ' ')).toBe('$ 25.000. Pijama largo de algodón.');
  });

  it('given a name that closes the script element, when the page renders, then the data stays one script and reads back the same', async () => {
    stubProduct(productDetail({ name: 'Pijama </script><b>x</b>' }));

    renderRoute('/producto/luna');

    await screen.findByRole('heading', { level: 1, name: 'Pijama </script><b>x</b>' });
    expect(structuredData().name).toBe('Pijama </script><b>x</b>');
    expect(document.querySelector('b')).toBeNull();
  });

  it('given a long description, when it is cut, then it ends at a word with "…" within 200 characters', () => {
    const text = 'Pijama suave de algodón para las noches de verano. '.repeat(6);

    const cut = cutDescription(text);

    expect(cut.length).toBeLessThanOrEqual(200);
    expect(cut).toMatch(/[a-zó]…$/);
    expect(text.startsWith(cut.slice(0, -1))).toBe(true);
    expect(cutDescription(' corto \n texto ')).toBe('corto texto');
  });

  it('given a category with filters in the URL, when its page renders, then its title and description carry its name and the canonical URL has no query', async () => {
    stubCatalogApi({ listings: [listed([product('luna')])], categories: shopCategories([{ slug: 'verano', name: 'Verano' }]) });

    renderRoute('/categoria/verano?talle=M&orden=PRICE_ASC');

    await waitFor(() => { expect(document.title).toBe(`Verano | ${es.store.name}`); });
    expect(description()).toBe(es.seo.categoryDescription('Verano'));
    expect(canonical()).toBe(`${window.location.origin}/categoria/verano`);
    expect(robots()).toBeNull();
  });

  it('given the home page, when it renders, then it carries its description and canonical URL', async () => {
    stubCatalogApi({ listings: [listed([product('luna')])] });

    renderRoute('/');

    await waitFor(() => { expect(description()).toBe(es.seo.homeDescription); });
    expect(canonical()).toBe(`${window.location.origin}/`);
    expect(robots()).toBeNull();
  });

  it.each(['/cuenta/ingresar', '/buscar?q=pijama', '/checkout', '/pedido/abc', '/no-existe'])(
    'given %s, when it renders, then it is kept out of search engines',
    async (path) => {
      stubCatalogApi({ listings: [listed([])] });

      renderRoute(path);

      await waitFor(() => { expect(robots()?.getAttribute('content')).toBe('noindex'); });
      expect(canonical()).toBeUndefined();
    },
  );
});
