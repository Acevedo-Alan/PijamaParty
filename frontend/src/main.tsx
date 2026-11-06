import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import '@fontsource/inter/700.css';
import './styles/tokens.css';
import './styles/base.css';
import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { createQueryClient } from './api/queryClient.ts';
import { MotionProvider } from './components/motion/MotionProvider.tsx';
import { sessionQuery } from './features/admin/useSession.ts';
import { createAppRoutes } from './routes.tsx';

const noop = () => undefined;

const container = document.getElementById('root');
if (container === null) {
  throw new Error('Missing #root element');
}

const queryClient = createQueryClient();
// The session is asked for early and its failure is left to the back office layout, which asks again (T-E8-22).
const router = createBrowserRouter(
  createAppRoutes(() => queryClient.query(sessionQuery).then(noop, noop)),
);

createRoot(container).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <MotionProvider>
        <RouterProvider router={router} />
      </MotionProvider>
    </QueryClientProvider>
  </StrictMode>,
);
