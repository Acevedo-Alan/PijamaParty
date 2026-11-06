import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { listed, stubCatalogApi } from '../../test/catalogApi.ts';
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
