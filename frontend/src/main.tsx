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
import { routes } from './routes.tsx';

const container = document.getElementById('root');
if (container === null) {
  throw new Error('Missing #root element');
}

createRoot(container).render(
  <StrictMode>
    <QueryClientProvider client={createQueryClient()}>
      <MotionProvider>
        <RouterProvider router={createBrowserRouter(routes)} />
      </MotionProvider>
    </QueryClientProvider>
  </StrictMode>,
);
