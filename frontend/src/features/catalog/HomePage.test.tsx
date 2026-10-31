import { act, fireEvent, render, screen, within } from '@testing-library/react';
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

describe('carousel that waits while used or unseen, with swipe and a timed pill (UC-30, Apple HIG)', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    forgetMotionPreference();
    setTabHidden(false);
  });

  function renderCarousel() {
    vi.useFakeTimers();
    render(<HeroCarousel onDiscover={vi.fn()} />);
    return screen.getByRole('region', { name: carousel.label });
  }

  function wait(ms: number) {
    act(() => {
      vi.advanceTimersByTime(ms);
    });
  }

  function swipe(hero: HTMLElement, from: number, to: number) {
    // jsdom has no pointer capture, which the swipe takes once a drag starts.
    HTMLElement.prototype.setPointerCapture = vi.fn();
    fireEvent.pointerDown(hero, { pointerId: 1, pointerType: 'touch', clientX: from, clientY: 100 });
    fireEvent.pointerMove(hero, { pointerId: 1, pointerType: 'touch', clientX: (from + to) / 2, clientY: 102 });
    fireEvent.pointerUp(hero, { pointerId: 1, pointerType: 'touch', clientX: to, clientY: 102 });
  }

  function setTabHidden(hidden: boolean) {
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: hidden ? 'hidden' : 'visible' });
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
  }

  it.each([false, true])('given the carousel (reduced motion: %s), when swiped left and right, then it moves to the next slide and back', (reduced) => {
    preferReducedMotion(reduced);
    const hero = renderCarousel();

    swipe(hero, 300, 60);
    expect(slide(2)).toHaveAttribute('aria-current', 'true');
    swipe(hero, 60, 300);
    expect(slide(1)).toHaveAttribute('aria-current', 'true');
  });

  it('given the keyboard focus on a slide picker, when time passes, then it waits; once the focus leaves, the slide gets the time it had left', () => {
    preferReducedMotion(false);
    renderCarousel();

    wait(SLIDE_MS / 2);
    // No pointer pressed: the keyboard's focus.
    act(() => {
      slide(1).focus();
    });
    wait(SLIDE_MS * 2);
    expect(slide(1)).toHaveAttribute('aria-current', 'true');

    act(() => {
      slide(1).blur();
    });
    wait(SLIDE_MS / 2 - 1);
    expect(slide(1)).toHaveAttribute('aria-current', 'true');
    wait(1);
    expect(slide(2)).toHaveAttribute('aria-current', 'true');
  });

  it('given a slide picker pressed with the mouse, when it keeps the focus, then the carousel still advances', () => {
    preferReducedMotion(false);
    renderCarousel();

    fireEvent.pointerDown(slide(3), { pointerId: 1, pointerType: 'mouse', button: 0 });
    act(() => {
      slide(3).focus();
      window.dispatchEvent(new Event('pointerup'));
    });
    fireEvent.click(slide(3));
    expect(slide(3)).toHaveFocus();
    wait(SLIDE_MS);

    // From the last slide on to the first: the focus the press left does not stop it.
    expect(slide(1)).toHaveAttribute('aria-current', 'true');
  });

  it('given a slide picker tapped on a phone, when the focus comes after the finger lifts, then the carousel still advances', () => {
    preferReducedMotion(false);
    renderCarousel();

    // A phone's order: the press, the lift, and only then the focus and the click.
    fireEvent.pointerDown(slide(2), { pointerId: 1, pointerType: 'touch' });
    act(() => {
      window.dispatchEvent(new Event('pointerup'));
    });
    act(() => {
      slide(2).focus();
    });
    fireEvent.click(slide(2));
    wait(SLIDE_MS);

    expect(slide(3)).toHaveAttribute('aria-current', 'true');
  });

  it('given the focus on the pause button, when time passes, then it still advances: that button controls it', () => {
    preferReducedMotion(false);
    renderCarousel();

    act(() => {
      screen.getByRole('button', { name: carousel.pause }).focus();
    });
    wait(SLIDE_MS);

    expect(slide(2)).toHaveAttribute('aria-current', 'true');
  });

  it('given a finger on the carousel, when time passes, then it waits until the finger lifts, even outside it', () => {
    preferReducedMotion(false);
    const hero = renderCarousel();

    fireEvent.pointerDown(hero, { pointerId: 1, pointerType: 'touch', clientX: 100, clientY: 100 });
    wait(SLIDE_MS * 2);
    expect(slide(1)).toHaveAttribute('aria-current', 'true');

    act(() => {
      window.dispatchEvent(new Event('pointerup'));
    });
    wait(SLIDE_MS);
    expect(slide(2)).toHaveAttribute('aria-current', 'true');
  });

  it('given a hidden tab, when time passes, then it waits until the tab shows again', () => {
    preferReducedMotion(false);
    renderCarousel();

    setTabHidden(true);
    wait(SLIDE_MS * 2);
    expect(slide(1)).toHaveAttribute('aria-current', 'true');

    setTabHidden(false);
    wait(SLIDE_MS);
    expect(slide(2)).toHaveAttribute('aria-current', 'true');
  });

  it('given the carousel scrolled off the screen, when time passes, then it waits until it is back', () => {
    const report: { see?: (seen: boolean) => void } = {};
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: IntersectionObserverCallback) {
        report.see = (seen) => {
          callback([{ isIntersecting: seen }] as never, {} as never);
        };
      }

      observe() {
        // The test reports what is on screen.
      }

      disconnect() {
        // Nothing to release.
      }
    });
    preferReducedMotion(false);
    renderCarousel();

    act(() => {
      report.see?.(false);
    });
    wait(SLIDE_MS * 2);
    expect(slide(1)).toHaveAttribute('aria-current', 'true');

    act(() => {
      report.see?.(true);
    });
    wait(SLIDE_MS);
    expect(slide(2)).toHaveAttribute('aria-current', 'true');
  });

  it('given the carousel advancing, when a slide shows, then its pill holds the timed fill, frozen while it waits', () => {
    preferReducedMotion(false);
    renderCarousel();

    const fill = () => slide(1).querySelector('.hero-dot-progress');
    expect(fill()).toHaveStyle({ animationDuration: `${String(SLIDE_MS)}ms` });
    expect(slide(2).querySelector('.hero-dot-progress')).toBeNull();
    expect(slide(1).parentElement).not.toHaveClass('waiting');

    act(() => {
      screen.getByRole('button', { name: carousel.pause }).click();
    });
    expect(slide(1).parentElement).toHaveClass('waiting');
    expect(fill()).not.toBeNull();
  });

  it('given reduced motion, when the carousel shows, then no pill fills', () => {
    preferReducedMotion(true);
    renderCarousel();

    expect(document.querySelector('.hero-dot-progress')).toBeNull();
  });
});
