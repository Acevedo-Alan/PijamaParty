import { expect, test } from '@playwright/test';
import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { photoPng } from '../support/photo.ts';

/**
 * T-E6-07 (RNF-31.2): the production Caddy image (lighthouse/caddy.setup.ts) sends only the link-preview bots of
 * WhatsApp, Facebook, Instagram, X and Telegram to the API's preview of /producto/{slug}; shoppers, also inside the
 * Instagram and Facebook apps, get the single-page app.
 */

const PREVIEW_BOTS = {
  facebook: 'facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)',
  whatsapp: 'WhatsApp/2.24.6.77 A',
};

const SHOPPERS = {
  chrome:
    'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36',
  instagram:
    'Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/AP2A.240805.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) ' +
    'Version/4.0 Chrome/126.0.6478.134 Mobile Safari/537.36 Instagram 339.0.0.30.105 Android',
  facebook:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 ' +
    '[FBAN/FBIOS;FBAV/470.0.0.40.97;FBBV/621497562;FBDV/iPhone15,3;FBMD/iPhone;FBSN/iOS;FBSV/17.5;FBSS/3;FBLC/es_LA]',
};

let product: ProductForSale;

test.beforeAll(async ({ browser }) => {
  const context = await adminContext(browser);
  const admin = await AdminApi.open(context);
  product = await admin.createProductForSale({
    name: 'Pijama Compartido',
    sizes: ['M'],
    colors: ['Rosa'],
    priceCents: 1_850_000,
    stock: 10,
  });
  await admin.uploadPhoto(product.id, photoPng(2));
  await context.close();
});

/** The content of an Open Graph tag of the page. */
function tag(html: string, property: string): string | undefined {
  return new RegExp(`<meta property="${property}" content="([^"]*)">`).exec(html)?.[1];
}

for (const [bot, userAgent] of Object.entries(PREVIEW_BOTS)) {
  test(`the ${bot} link preview of a product gets its name, price and photo`, async ({ request }) => {
    const response = await request.get(`/producto/${product.slug}`, { headers: { 'User-Agent': userAgent } });

    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('text/html');
    expect(response.headers()['vary']).toContain('User-Agent');
    const html = await response.text();
    expect(tag(html, 'og:title')).toBe(`${product.name} | Pijama Party Santa Fe`);
    // The price, then the description the test helper gives every product.
    expect(tag(html, 'og:description')).toMatch(/^\$ 18\.500\. \S/);
    expect(tag(html, 'og:url')).toMatch(new RegExp(`/producto/${product.slug}$`));
    const image = tag(html, 'og:image');
    expect(image).toMatch(/\/uploads\/[^/]+-1200\.webp$/);
    // The photo the preview names is served by Caddy.
    const photo = await request.get(new URL(image!).pathname);
    expect(photo.status()).toBe(200);
    expect(photo.headers()['content-type']).toBe('image/webp');
  });
}

test('a link preview of a product nobody can see shows the store', async ({ request }) => {
  const response = await request.get('/producto/no-such-product', {
    headers: { 'User-Agent': PREVIEW_BOTS.whatsapp },
  });

  expect(response.status()).toBe(404);
  expect(tag(await response.text(), 'og:title')).toBe('Pijama Party Santa Fe');
});

for (const [shopper, userAgent] of Object.entries(SHOPPERS)) {
  test(`a shopper on ${shopper} gets the store's single-page app`, async ({ request }) => {
    const response = await request.get(`/producto/${product.slug}`, { headers: { 'User-Agent': userAgent } });

    expect(response.status()).toBe(200);
    expect(response.headers()['vary']).toContain('User-Agent');
    const html = await response.text();
    expect(html).toContain('<div id="root"></div>');
    expect(tag(html, 'og:type')).toBeUndefined();
  });
}
