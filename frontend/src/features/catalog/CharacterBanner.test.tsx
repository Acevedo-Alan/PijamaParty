import { screen, within } from '@testing-library/react';
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

  it('given a hidden banner, when the home page opens, then its section is not shown', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])], banner: answer(204) });

    renderRoute('/');

    expect(await screen.findByRole('link', { name: /Pijama rosa/ })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { level: 2, name: BANNER.title })).not.toBeInTheDocument();
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
