import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { es } from './i18n/es.ts';
import { routes } from './routes.tsx';

function renderAt(path: string) {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />);
}

describe('routes', () => {
  it('given the root path, when the app loads, then the home page is shown', () => {
    renderAt('/');

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(es.home.title);
  });

  it('given an unknown path, when the app loads, then the own 404 page links back to the store', () => {
    renderAt('/no/such/page');

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(es.notFound.title);
    expect(screen.getByRole('link', { name: es.notFound.backToStore })).toHaveAttribute('href', '/');
  });
});
