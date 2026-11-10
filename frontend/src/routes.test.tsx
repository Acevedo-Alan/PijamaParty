import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, matchRoutes, RouterProvider } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from './api/client.ts';
import { es } from './i18n/es.ts';
import { createAppRoutes, routes } from './routes.tsx';
import { renderRoute } from './test/renderRoute.tsx';

describe('routes', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the root path, when the app loads, then the home page welcomes with the whole catalog below', () => {
    vi.spyOn(api, 'GET').mockReturnValue(new Promise(() => undefined));

    renderRoute('/');

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(es.home.title);
    expect(screen.getByRole('heading', { level: 2, name: es.catalog.allTitle })).toBeInTheDocument();
  });

  it('given an unknown path, when the app loads, then the own 404 page links back to the store', () => {
    renderRoute('/no/such/page');

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(es.notFound.title);
    expect(screen.getByRole('link', { name: es.notFound.backToStore })).toHaveAttribute('href', '/');
  });

  it('given an unknown path, when the 404 page opens, then the chibi wonders over its map above the heading, hidden from screen readers (T-E7-49)', async () => {
    renderRoute('/no/such/page');

    // Lazy: its place is kept, with the same box, until it arrives.
    const heading = screen.getByRole('heading', { level: 1 });
    await waitFor(() => {
      expect(heading.previousElementSibling).toHaveClass('chibi');
    });
    const chibi = heading.previousElementSibling;
    expect(chibi).toHaveClass('chibi');
    expect(chibi).toHaveAttribute('aria-hidden', 'true');
    expect(chibi).toHaveAttribute('data-mood', 'puzzled');
    expect(chibi).toHaveAttribute('data-pose', 'lost');
    expect(chibi?.querySelector('.chibi-map')).not.toBeNull();
    expect(chibi?.querySelector('.chibi-wonder')).not.toBeNull();
  });

  it.each([
    '/checkout',
    '/checkout/result',
    '/pedido/some-token',
    '/arrepentimiento',
    '/preguntas-frecuentes',
    '/cuenta',
    '/cuenta/ingresar',
    '/cuenta/pedidos/some-order',
  ])('given %s, when the routes are built, then its page is loaded on demand, out of the main bundle (T-E6-02)', (path) => {
    const page = matchRoutes(routes, path)?.at(-1)?.route;

    expect(page?.lazy).toBeTypeOf('function');
    expect(page?.element).toBeUndefined();
  });

  it.each(['/', '/categoria/pijamas', '/producto/luna', '/buscar'])(
    'given %s, where shoppers arrive from links, when the routes are built, then its page is in the main bundle',
    (path) => {
      const page = matchRoutes(routes, path)?.at(-1)?.route;

      expect(page?.lazy).toBeUndefined();
      expect(page?.element).toBeDefined();
    },
  );

  it('given a page loaded on demand, when it is opened directly by URL, then it shows (T-E6-02)', async () => {
    vi.spyOn(api, 'GET').mockReturnValue(new Promise(() => undefined));
    const router = createMemoryRouter(routes, { initialEntries: ['/preguntas-frecuentes'] });

    render(
      <QueryClientProvider client={new QueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('heading', { level: 1, name: es.faq.title })).toBeInTheDocument();
  });
});

describe('back office loading (T-E8-22)', () => {
  it('given the back office layout, when its chunk is requested, then the session request starts with it', async () => {
    const prefetch = vi.fn(() => Promise.resolve());
    const admin = createAppRoutes(prefetch)[0]?.children?.find((route) => route.path === '/admin');

    expect(prefetch).not.toHaveBeenCalled();
    const lazy = admin?.lazy;
    const loaded = typeof lazy === 'function' ? await lazy() : undefined;

    expect(prefetch).toHaveBeenCalledOnce();
    expect(loaded).toHaveProperty('Component');
  });
});
