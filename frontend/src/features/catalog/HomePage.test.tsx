import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { answer, listed, product, products, queriesTo, stubCatalogApi } from '../../test/catalogApi.ts';
import { forgetMotionPreference, preferReducedMotion } from '../../test/reducedMotion.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { HeroCarousel, SLIDE_MS } from './HeroCarousel.tsx';

const PRODUCTS = '/api/catalog/products';

const BANNER = {
  title: 'La noche se llena de personajes',
  body: '',
  linkText: 'Ver kigurumis',
  linkPath: '/categoria/kigurumis',
  image: null,
};

const { carousel } = es.home;
const listingHeading = () => screen.getByRole('heading', { level: 2, name: es.catalog.allTitle });
const slide = (position: number) => screen.getByRole('button', { name: carousel.showSlide(position, 3) });

describe('home page (UC-30)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the home page, when it loads, then the carousel, the banner, "Elegí para quién" and the grid appear in that order, with the withdrawal link only in the footer', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])], banner: answer(200, BANNER) });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    const sections = [
      screen.getByRole('region', { name: carousel.label }),
      await screen.findByRole('region', { name: BANNER.title }),
      screen.getByRole('region', { name: es.home.audienceTitle }),
      screen.getByRole('region', { name: es.catalog.allTitle }),
    ];
    sections.slice(1).forEach((section, index) => {
      const position = sections[index]?.compareDocumentPosition(section) ?? 0;
      expect(position & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });
    expect(screen.queryByRole('region', { name: es.withdrawal.link })).not.toBeInTheDocument();
    expect(within(screen.getByRole('contentinfo')).getByRole('link', { name: es.withdrawal.link })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(es.home.title);
    expect(within(screen.getByRole('region', { name: es.catalog.allTitle })).getByText(es.home.listingText))
      .toBeInTheDocument();
  });

  it('given "Mujer" in "Elegí para quién", when chosen, then the grid is filtered by audience WOMEN and the URL has publico=mujer, keeping the sort', async () => {
    const get = stubCatalogApi({ listings: [listed([product('rosa')])] });
    const scroll = vi.spyOn(Element.prototype, 'scrollIntoView');
    const { router } = renderRoute('/?orden=menor-precio');
    const audience = screen.getByRole('region', { name: es.home.audienceTitle });

    await userEvent.click(within(audience).getByRole('link', { name: es.home.audiences.WOMEN }));

    expect(router.state.location.pathname).toBe('/');
    expect(router.state.location.search).toBe('?publico=mujer&orden=menor-precio');
    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(queriesTo(get, PRODUCTS).at(-1)).toEqual({ audience: ['WOMEN'], sort: 'PRICE_ASC', page: 0 });
    expect(scroll.mock.contexts.at(-1)).toBe(listingHeading());
    expect(listingHeading()).toHaveFocus();
  });

  it('given the three audience cards, when listed, then they open Mujer, Hombre and Niños', () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/');

    const links = within(screen.getByRole('region', { name: es.home.audienceTitle })).getAllByRole('link');
    expect(links.map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
      ['Mujer', '/?publico=mujer'],
      ['Hombre', '/?publico=hombre'],
      ['Niños', '/?publico=infantil'],
    ]);
  });

  it('given "Descubrí la colección", when pressed, then the page scrolls to "Todos los productos" and moves the focus to its heading', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    const scroll = vi.spyOn(Element.prototype, 'scrollIntoView');
    renderRoute('/');
    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(scroll).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('link', { name: es.home.discover }));

    expect(scroll.mock.contexts).toEqual([listingHeading()]);
    expect(listingHeading()).toHaveFocus();
  });

  it('given filters in the URL, when the home page opens, then it starts at "Todos los productos", again once the banner is in place', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])], banner: answer(200, BANNER) });
    const scroll = vi.spyOn(Element.prototype, 'scrollIntoView');

    renderRoute('/?talle=M');

    await screen.findByRole('region', { name: BANNER.title });
    expect(scroll.mock.contexts).toEqual([listingHeading(), listingHeading()]);
    expect(scroll).toHaveBeenLastCalledWith({ block: 'start', behavior: 'instant' });
  });

  it('given the home page, when the grid renders, then none of its photos load first: the carousel is the first screen', async () => {
    stubCatalogApi({ listings: [listed(products(4, 'p'))] });

    renderRoute('/');

    await screen.findAllByRole('link', { name: /Pijama p-/ });
    const photos = screen.getAllByTestId('blurred-image').map((image) => within(image).getByRole('img'));
    expect(photos.map((photo) => photo.getAttribute('loading'))).toEqual(['lazy', 'lazy', 'lazy', 'lazy']);
  });

  it('given a category, when it opens, then it shows only its listing, without the home sections', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/verano');

    expect(await screen.findByRole('link', { name: /Pijama rosa/ })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: carousel.label })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: es.home.audienceTitle })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(es.catalog.categoryTitle);
  });
});

describe('welcome carousel (UC-30, D-14)', () => {
  afterEach(() => {
    vi.useRealTimers();
    forgetMotionPreference();
  });

  function renderCarousel() {
    vi.useFakeTimers();
    render(<HeroCarousel onDiscover={vi.fn()} />);
  }

  function wait(ms: number) {
    act(() => {
      vi.advanceTimersByTime(ms);
    });
  }

  it('given the carousel, when 4 seconds pass, then the next slide shows, and after the last the first again', () => {
    preferReducedMotion(false);
    renderCarousel();
    expect(slide(1)).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('group', { name: carousel.slide(1, 3) })).toHaveAttribute('aria-hidden', 'false');

    wait(SLIDE_MS - 1);
    expect(slide(1)).toHaveAttribute('aria-current', 'true');
    wait(1);
    expect(slide(2)).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('group', { name: carousel.slide(2, 3) })).toHaveAttribute('aria-hidden', 'false');
    wait(SLIDE_MS);
    expect(slide(3)).toHaveAttribute('aria-current', 'true');
    wait(SLIDE_MS);
    expect(slide(1)).toHaveAttribute('aria-current', 'true');
  });

  it('given a visitor with reduced motion, when the home page opens, then the carousel does not advance by itself', () => {
    preferReducedMotion(true);
    renderCarousel();

    wait(SLIDE_MS);
    wait(SLIDE_MS);

    expect(slide(1)).toHaveAttribute('aria-current', 'true');
    expect(screen.queryByRole('button', { name: carousel.pause })).not.toBeInTheDocument();
    act(() => {
      slide(3).click();
    });
    expect(slide(3)).toHaveAttribute('aria-current', 'true');
  });

  it('given the carousel advancing, when pause is pressed, then it stops; pressing it again resumes it', () => {
    preferReducedMotion(false);
    renderCarousel();

    act(() => {
      screen.getByRole('button', { name: carousel.pause }).click();
    });
    wait(SLIDE_MS);
    wait(SLIDE_MS);
    expect(slide(1)).toHaveAttribute('aria-current', 'true');

    act(() => {
      screen.getByRole('button', { name: carousel.resume }).click();
    });
    wait(SLIDE_MS);
    expect(slide(2)).toHaveAttribute('aria-current', 'true');
  });

  it('given the carousel, when tabbing into it, then the pause button comes first', async () => {
    preferReducedMotion(false);
    render(<HeroCarousel onDiscover={vi.fn()} />);

    await userEvent.tab();

    expect(screen.getByRole('button', { name: carousel.pause })).toHaveFocus();
  });

  it('given slides with photos, when shown, then the first is preloaded and loads first and the others wait', () => {
    const photo = (name: string) => ({ src: `/assets/${name}-640.webp`, srcSet: `/assets/${name}-640.webp 640w`, alt: name });
    render(
      <HeroCarousel
        onDiscover={vi.fn()}
        slides={[
          { tone: 'hero', photo: photo('uno') },
          { tone: 'rose', photo: photo('dos') },
        ]}
      />,
    );

    const images = [screen.getByRole('img', { name: 'uno' }), screen.getByRole('img', { hidden: true, name: 'dos' })];
    expect(images.map((image) => [image.getAttribute('loading'), image.getAttribute('fetchpriority')])).toEqual([
      ['eager', 'high'],
      ['lazy', 'auto'],
    ]);
    expect(document.head.querySelector('link[rel="preload"][as="image"]')).toHaveAttribute(
      'imagesrcset',
      '/assets/uno-640.webp 640w',
    );
  });
});
