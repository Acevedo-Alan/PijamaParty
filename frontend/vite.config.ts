/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Same origin as production: Caddy serves the build at / and routes /api/* to the backend (E0 plan), so there is
// no CORS and cookies stay first-party. In dev, Vite proxies /api to the local backend.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
  },
});
