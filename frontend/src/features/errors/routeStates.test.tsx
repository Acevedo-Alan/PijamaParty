import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { withRoot } from '../../routes.tsx';

/** The routes under a query cache, as main.tsx renders them: the error page asks for the store's contact. */
function renderRouter(router: ReturnType<typeof createMemoryRouter>) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

describe('route states (T-E8-21)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('given a lazy page whose chunk fails, when it is opened, then the Spanish error page offers to reload', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const reload = vi.fn();
    vi.stubGlobal('location', { reload });
    const failing = withRoot([
      { path: '/roto', lazy: () => Promise.reject(new Error('Failed to fetch dynamically imported module')) },
    ]);
    renderRouter(createMemoryRouter(failing, { initialEntries: ['/roto'] }));

    expect(await screen.findByRole('heading', { level: 1, name: es.routeError.title })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: es.routeError.reload }));
    expect(reload).toHaveBeenCalledOnce();
    expect(screen.getByRole('link', { name: es.notFound.backToStore })).toHaveAttribute('href', '/');
  });

  it('given a slow lazy page, when navigating to it, then a status says it is loading until it arrives', async () => {
    type Page = { Component: () => ReactElement };
    let release: (page: Page) => void = () => undefined;
    const slow = withRoot([
      { path: '/', element: <p>inicio</p> },
      {
        path: '/lenta',
        lazy: () =>
          new Promise<Page>((resolve) => {
            release = resolve;
          }),
      },
    ]);
    const router = createMemoryRouter(slow, { initialEntries: ['/'] });
    renderRouter(router);

    void router.navigate('/lenta');
    expect(await screen.findByText(es.routeError.loading)).toBeInTheDocument();

    release({ Component: () => <h1>lista</h1> });
    expect(await screen.findByRole('heading', { name: 'lista' })).toBeInTheDocument();
    expect(screen.queryByText(es.routeError.loading)).not.toBeInTheDocument();
  });
});
