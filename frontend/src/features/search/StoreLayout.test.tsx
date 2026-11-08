import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { SHOW_LISTING } from '../catalog/HomePage.tsx';
import { listed, product, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

describe('the store layout and keyboard users (RNF-31.4, WCAG 2.4.1, 2.4.2, 2.4.3)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a page, when the keyboard starts, then the first stop skips to the page and focuses its heading', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/no-existe');
    const heading = await screen.findByRole('heading', { level: 1, name: es.notFound.title });

    await userEvent.tab();
    const skip = screen.getByRole('link', { name: es.skipToContent });
    expect(skip).toHaveFocus();
    await userEvent.keyboard('{Enter}');

    expect(heading).toHaveFocus();
  });

  it('given an unknown page, when it renders, then the tab has its own title', async () => {
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/no-existe');

    await waitFor(() => {
      expect(document.title).toBe(`${es.notFound.title} | ${es.store.name}`);
    });
  });

  it('given a page, when the visitor moves to another path, then focus goes to the new page heading', async () => {
    stubCatalogApi({ listings: [listed([])] });
    const { router } = renderRoute('/no-existe');
    await screen.findByRole('heading', { level: 1, name: es.notFound.title });

    await act(() => router.navigate('/tampoco-existe'));

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: es.notFound.title })).toHaveFocus();
    });
  });
});

describe('the scroll after a page change (UC-31)', () => {
  const scrolled = vi.spyOn(window, 'scrollTo');

  afterEach(() => {
    scrolled.mockClear();
  });

  async function openPage() {
    stubCatalogApi({ listings: [listed([])] });
    const view = renderRoute('/no-existe');
    await screen.findByRole('heading', { level: 1, name: es.notFound.title });
    return view.router;
  }

  it('given a link to another page, when the visitor follows it, then the new page opens at the top', async () => {
    const router = await openPage();
    expect(scrolled).not.toHaveBeenCalled();

    await act(() => router.navigate('/tampoco-existe'));

    expect(scrolled).toHaveBeenCalledWith({ top: 0, left: 0, behavior: 'instant' });
  });

  it('given the back button, when the visitor goes back, then the page is left to the browser', async () => {
    const router = await openPage();
    await act(() => router.navigate('/tampoco-existe'));
    scrolled.mockClear();

    await act(() => router.navigate(-1));

    expect(scrolled).not.toHaveBeenCalled();
  });

  it('given a query change on the same page, when it changes, then the page does not move', async () => {
    const router = await openPage();

    await act(() => router.navigate('/no-existe?orden=precio'));

    expect(scrolled).not.toHaveBeenCalled();
  });

  it('given a header link to the home listing (UC-31), when it opens the home page, then the page scrolls to the listing after going to the top', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });
    const view = renderRoute('/no-existe');
    await screen.findByRole('heading', { level: 1, name: es.notFound.title });
    const toListing = vi.spyOn(Element.prototype, 'scrollIntoView');

    await act(() => view.router.navigate('/', { state: SHOW_LISTING }));

    await waitFor(() => {
      expect(toListing).toHaveBeenCalled();
    });
    const top = scrolled.mock.invocationCallOrder[0] ?? Infinity;
    const listing = toListing.mock.invocationCallOrder[0] ?? 0;
    expect(top).toBeLessThan(listing);
  });

  it('given a link with a hash, when it opens, then the page does not jump to the top', async () => {
    const router = await openPage();

    await act(() => router.navigate('/tampoco-existe#contactanos'));

    expect(scrolled).not.toHaveBeenCalled();
  });
});
