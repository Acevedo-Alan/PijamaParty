import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { answer, listed, product, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const BANNER = {
  title: 'La noche se llena de personajes',
  body: 'Una pijamada para jugar, soñar y descansar.',
  linkText: 'Descubrí la colección',
  linkPath: '/categoria/kigurumis',
  image: {
    placeholder: 'data:image/webp;base64,AAAA',
    files: [
      { width: 400, url: '/uploads/banner-400.webp' },
      { width: 800, url: '/uploads/banner-800.webp' },
      { width: 1200, url: '/uploads/banner-1200.webp' },
    ],
  },
};

const banner = () => screen.getByRole('region', { name: BANNER.title });

describe('character banner of the home page (UC-32)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a published banner, when the home page opens, then it shows the photo, the texts and a link into the store', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])], banner: answer(200, BANNER) });

    renderRoute('/');

    expect(await screen.findByRole('heading', { level: 2, name: BANNER.title })).toBeInTheDocument();
    expect(within(banner()).getByText(BANNER.body)).toBeInTheDocument();
    expect(within(banner()).getByRole('link', { name: /Descubrí la colección/ })).toHaveAttribute(
      'href',
      '/categoria/kigurumis',
    );
    const photo = within(banner()).getByRole('presentation');
    expect(photo).toHaveAttribute(
      'srcset',
      '/uploads/banner-400.webp 400w, /uploads/banner-800.webp 800w, /uploads/banner-1200.webp 1200w',
    );
    expect(await screen.findByRole('heading', { level: 2, name: es.catalog.allTitle })).toBeInTheDocument();
  });

  it('given no carousel photo yet (D-15), when the home page opens, then the banner photo, the first on the screen, loads at once and first (RNF-11)', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])], banner: answer(200, BANNER) });

    renderRoute('/');

    await screen.findByRole('heading', { level: 2, name: BANNER.title });
    const photo = within(banner()).getByRole('presentation');
    expect(photo).toHaveAttribute('loading', 'eager');
    expect(photo).toHaveAttribute('fetchpriority', 'high');
  });

  it('given a hidden banner, when the home page opens, then its section is not shown', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])], banner: answer(204) });

    renderRoute('/');

    expect(await screen.findByRole('link', { name: /Pijama rosa/ })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { level: 2, name: BANNER.title })).not.toBeInTheDocument();
  });

  it('given the banner still loading, when the home page shows, then its place holds the banner size without texts or link', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])], banner: new Promise(() => {}) });

    const { container } = renderRoute('/');

    expect(await screen.findByRole('link', { name: /Pijama rosa/ })).toBeInTheDocument();
    const place = container.querySelector('.character-banner');
    expect(place).toHaveAttribute('aria-hidden', 'true');
    expect(place?.querySelector('.ph.character-banner-art')).not.toBeNull();
    expect(place).toHaveTextContent('');
  });

  it('given a hidden banner, when the home page has its answer, then the held place closes', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])], banner: answer(204) });

    const { container } = renderRoute('/');

    await waitFor(() => {
      expect(container.querySelector('.character-banner')).toBeNull();
    });
  });

  it('given a category page, when it opens, then it shows only the listing and never asks for the banner', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])], banner: answer(200, BANNER) });

    renderRoute('/categoria/verano');

    expect(await screen.findByRole('link', { name: /Pijama rosa/ })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: BANNER.title })).not.toBeInTheDocument();
    expect(get.mock.calls.map((call): unknown => call[0])).not.toContain('/api/home/banner');
  });

  it('given a target that is not a path of the store, when shown, then it is not a link', async () => {
    stubCatalogApi({ listings: [listed([])], banner: answer(200, { ...BANNER, linkPath: '//other.site' }) });

    renderRoute('/');

    await screen.findByRole('heading', { level: 2, name: BANNER.title });
    expect(within(banner()).queryByRole('link')).not.toBeInTheDocument();
    expect(within(banner()).getByText(/Descubrí la colección/)).toBeInTheDocument();
  });
});
