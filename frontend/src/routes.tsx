import type { RouteObject } from 'react-router';
import { NotFoundPage } from './features/errors/NotFoundPage.tsx';
import { HomePage } from './features/home/HomePage.tsx';

/** Feature folders add their routes here (catalog, cart, checkout, account, admin: ARCHITECTURE.md section 11). */
export const routes: RouteObject[] = [
  { path: '/', element: <HomePage /> },
  { path: '*', element: <NotFoundPage /> },
];
