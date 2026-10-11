import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from './api/client.ts';
import { es } from './i18n/es.ts';
import { renderRoute } from './test/renderRoute.tsx';

describe('routes', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the root path, when the app loads, then the whole catalog is shown', () => {
    vi.spyOn(api, 'GET').mockReturnValue(new Promise(() => undefined));

    renderRoute('/');

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(es.catalog.allTitle);
  });

  it('given an unknown path, when the app loads, then the own 404 page links back to the store', () => {
    renderRoute('/no/such/page');

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(es.notFound.title);
    expect(screen.getByRole('link', { name: es.notFound.backToStore })).toHaveAttribute('href', '/');
  });
});
