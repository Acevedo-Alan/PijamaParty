import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { answer, detailImage, detailVariant, listed, productDetail, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { readRecentlyViewed, rememberViewed } from './recentlyViewed.ts';

const DETAIL = '/api/catalog/products/{slug}';

const viewed = (slug: string) => productDetail({ slug, name: `Pijama ${slug}`, images: [detailImage(slug, null)] });

/** The catalog of the home page and the product pages, with these details by slug; any other is unlisted. */
function stubDetails(details: Record<string, unknown>) {
  const get = stubCatalogApi({ listings: [listed([])] });
  const catalog = get.getMockImplementation();
  get.mockImplementation((path: string, init?: { params?: { path?: { slug?: string } } }) => {
    const slug = init?.params?.path?.slug ?? '';
    if (path === DETAIL) {
      return Promise.resolve(slug in details ? answer(200, details[slug]) : answer(404));
    }
    return catalog?.(path, init) as Promise<unknown>;
  });
}

const recent = () => screen.findByRole('region', { name: es.product.recentlyViewed });
const cards = (section: HTMLElement) => within(section).getAllByRole('link').map((link) => link.getAttribute('href'));

describe('recently viewed (T-E7-33)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given products A, B and C viewed in that order, when the home page opens, then they show newest first', async () => {
    ['a', 'b', 'c'].forEach(rememberViewed);
    stubDetails({ a: viewed('a'), b: viewed('b'), c: viewed('c') });

    renderRoute('/');

    expect(cards(await recent())).toEqual(['/producto/c', '/producto/b', '/producto/a']);
  });

  it('given A, B and C viewed, when B opens, then it goes first in the browser and the page shows C and A', async () => {
    ['a', 'b', 'c'].forEach(rememberViewed);
    stubDetails({ a: viewed('a'), b: viewed('b'), c: viewed('c') });

    renderRoute('/producto/b');

    expect(cards(await recent())).toEqual(['/producto/c', '/producto/a']);
    expect(readRecentlyViewed()).toEqual(['b', 'c', 'a']);
  });

  it('given a viewed product no longer listed, when the list shows, then it is left out and forgotten', async () => {
    ['gone', 'a'].forEach(rememberViewed);
    stubDetails({ a: viewed('a') });

    renderRoute('/');

    expect(cards(await recent())).toEqual(['/producto/a']);
    await waitFor(() => {
      expect(readRecentlyViewed()).toEqual(['a']);
    });
  });

  it('given a viewed product, when its card shows, then it carries the server price and only the colors with stock', async () => {
    rememberViewed('luna');
    const variants = [detailVariant('M', 'Rosa', 2, 1_800_000), detailVariant('M', 'Celeste', 0, 1_800_000)];
    stubDetails({ luna: productDetail({ fromPriceCents: 1_800_000, variants }) });

    renderRoute('/');

    const card = within(await recent()).getByRole('link', { name: /Pijama Luna/ });
    expect(card).toHaveTextContent(formatMoney(1_800_000).replace(/\s/g, ' '));
    expect(within(card).getByText(es.catalog.colors(['Rosa']))).toBeInTheDocument();
    expect(within(card).queryByText(es.catalog.isNew)).not.toBeInTheDocument();
  });

  it('given a first visit, when the home page opens, then there is no "Vistos recientemente"', async () => {
    stubDetails({});

    renderRoute('/');

    expect(await screen.findByRole('heading', { name: es.catalog.allTitle })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: es.product.recentlyViewed })).not.toBeInTheDocument();
  });
});
