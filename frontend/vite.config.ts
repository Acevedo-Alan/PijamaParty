/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';

/** The Latin file of the body text weight (Quicksand 500, styles/base.css), which every page paints with. */
const BODY_FONT = /\/quicksand-latin-500-normal-[^/]+\.woff2$/;

/**
 * T-E6-02 (RNF-11): preloads the body font from index.html, so its download starts with the HTML instead of after
 * the CSS. Its file name carries a hash, so the tag is written at build time; a build without it fails.
 */
function preloadBodyFont(): Plugin {
  return {
    name: 'preload-body-font',
    apply: 'build',
    transformIndexHtml: {
      order: 'post',
      handler(_html, { bundle }) {
        const font = Object.keys(bundle ?? {}).find((fileName) => BODY_FONT.test(`/${fileName}`));
        if (font === undefined) {
          throw new Error('The body font (Quicksand 500, latin, woff2) is not in the build');
        }
        return [
          {
            tag: 'link',
            attrs: { rel: 'preload', href: `/${font}`, as: 'font', type: 'font/woff2', crossorigin: '' },
            injectTo: 'head',
          },
        ];
      },
    },
  };
}

// Same origin as production: Caddy serves the build at / and routes /api/* to the backend (E0 plan), so there is
// no CORS and cookies stay first-party. In dev, Vite proxies /api to the local backend.
export default defineConfig({
  plugins: [react(), preloadBodyFont()],
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
