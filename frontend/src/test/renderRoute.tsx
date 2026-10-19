import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { MotionProvider } from '../components/motion/MotionProvider.tsx';
import { routes } from '../routes.tsx';

/** A route with its on-demand page already loaded, and the same for its children. */
async function loaded(route: RouteObject): Promise<RouteObject> {
  const { lazy, children, ...rest } = route;
  const page = typeof lazy === 'function' ? await lazy() : {};
  const loadedChildren = children === undefined ? {} : { children: await Promise.all(children.map(loaded)) };
  return { ...rest, ...page, ...loadedChildren } as RouteObject;
}

/**
 * The app's routes with every on-demand page loaded up front (T-E6-02), so a page renders in the same tick as
 * without code splitting. routes.test.tsx checks the on-demand loading itself.
 */
const loadedRoutes = await Promise.all(routes.map(loaded));

/**
 * Renders the app at a path with a fresh query cache that never retries, so a failed request shows at once. The
 * router is returned to check the URL the app navigates to. Without `motion` nothing animates, as before Motion's
 * features load; with it the app animates as in main.tsx.
 */
export function renderRoute(path: string, { motion = false }: { motion?: boolean } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(loadedRoutes, { initialEntries: [path] });
  const app = <RouterProvider router={router} />;
  const view = render(
    <QueryClientProvider client={client}>{motion ? <MotionProvider>{app}</MotionProvider> : app}</QueryClientProvider>,
  );
  return { ...view, router };
}
