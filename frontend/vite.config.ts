/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { createReadStream, existsSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';
import { defineConfig, type Plugin } from 'vite';

/** The Latin file of the body text weight (Inter 500, styles/base.css), which every page paints with. */
const BODY_FONT = /\/inter-latin-500-normal-[^/]+\.woff2$/;

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
          throw new Error('The body font (Inter 500, latin, woff2) is not in the build');
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

/**
 * UC-06, dev only: the uploaded photos at /uploads/*, as Caddy serves them in production (deploy/caddy/Caddyfile):
 * WebP files only, from the backend's UPLOADS_DIR (its default /srv/uploads resolves to the same folder for Node and
 * Java). Only the file name is used, so a request never leaves that folder.
 */
function serveUploads(): Plugin {
  const folder = resolve(process.env.UPLOADS_DIR ?? '/srv/uploads');
  return {
    name: 'serve-uploads',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/uploads', (request, response) => {
        const name = basename(decodeURIComponent((request.url ?? '').split('?')[0] ?? ''));
        const file = join(folder, name);
        if (!name.endsWith('.webp') || !existsSync(file)) {
          response.statusCode = 404;
          response.end();
          return;
        }
        response.setHeader('Content-Type', 'image/webp');
        createReadStream(file).pipe(response);
      });
    },
  };
}

// Same origin as production: Caddy serves the build at / and routes /api/* to the backend (E0 plan), so there is
// no CORS and cookies stay first-party. In dev, Vite proxies /api to the local backend and serves the uploads.
export default defineConfig({
  plugins: [react(), preloadBodyFont(), serveUploads()],
  build: {
    // Fonts are always files: the CSP's font-src allows 'self' only (T-E6-03), and a small subset inlined as a data:
    // URL (Open Sans greek-ext is under the 4 KB default) would be blocked and weigh on the render-blocking CSS.
    assetsInlineLimit: (file) => (/\.woff2?$/.test(file) ? false : undefined),
  },
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
