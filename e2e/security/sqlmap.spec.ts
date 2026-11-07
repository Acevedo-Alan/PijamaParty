import { expect, test } from '@playwright/test';
import { AdminApi, adminContext, type ProductForSale } from '../support/admin.ts';
import { BACKEND_URL } from '../stubs/settings.ts';
import { SessionProxy } from './sessionProxy.ts';
import { buildSqlmapImage, runSqlmap, type SqlmapTarget } from './sqlmap.ts';

/**
 * T-E6-19 (RNF-28): sqlmap against every route with a parameter the spec names (filters, search, cart, coupon,
 * checkout, withdrawal, sign-in) plus the tracking link and the back office's lists, which take typed search text. The
 * proxy of sessionProxy.ts supplies the CSRF token, the cart and the administrator's session, so each payload reaches
 * the code behind the validation. A target fails when sqlmap finds an injection point, and also when it cannot reach a
 * verdict, so a broken harness never passes for a clean application.
 */

let proxy: SessionProxy | undefined;
let port: number;
let product: ProductForSale;

test.beforeAll(async ({ browser }) => {
  buildSqlmapImage();
  const context = await adminContext(browser);
  const admin = await AdminApi.open(context);
  product = await admin.createProductForSale({
    name: 'Pijama sqlmap', sizes: ['M'], colors: ['Rosa'], priceCents: 2_000_000, stock: 10_000,
  });
  // Every valid checkout of sqlmap's baseline places an order: the stock must not run out (an adjustment takes 10,000)
  for (let top = 0; top < 9; top++) {
    await admin.adjustStock(product.variants[0]?.id ?? '', 10_000);
  }
  await admin.setShippingCost('SANTA_FE', 500_000);
  await admin.createCoupon({ code: 'SQLMAP10', type: 'PERCENTAGE', discountValue: 10 });

  const cookies = await context.cookies(BACKEND_URL);
  const csrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN');
  if (csrf === undefined) {
    throw new Error("The administrator's session has no XSRF-TOKEN cookie");
  }
  proxy = new SessionProxy({
    backend: BACKEND_URL,
    administrator: {
      cookie: cookies.map((cookie) => `${cookie.name}=${cookie.value}`).join('; '),
      csrfToken: decodeURIComponent(csrf.value),
    },
    cartVariantId: product.variants[0]?.id ?? '',
  });
  port = await proxy.start();
  await admin.close();
});

test.afterAll(async () => {
  await proxy?.stop();
});

const BUYER = {
  firstName: 'Ana', lastName: 'Pérez', email: 'ana@example.com', phone: '3424000000', nationalId: '30123456',
};
const ADDRESS = {
  street: 'San Martín', number: '2000', city: 'Santa Fe', province: 'SANTA_FE', postalCode: 'S3000ABC',
};
const CREDENTIALS = { email: 'ana@example.com', password: 'una-contraseña-larga' };

type Build = (product: ProductForSale) => Omit<SqlmapTarget, 'name'>;

/** Each route with the values a normal request would carry; sqlmap varies them. `*` marks a path segment to test. */
const TARGETS: [name: string, build: Build][] = [
  // The shop window: filters, search and the product page
  [
    'GET /api/catalog/products',
    (p) => ({
      path: `/api/catalog/products?q=pijama&category=${p.category.slug}&minPrice=100&maxPrice=5000000&page=0`,
    }),
  ],
  ['GET /api/catalog/suggestions', () => ({ path: '/api/catalog/suggestions?q=pijama' })],
  ['GET /api/catalog/products/{slug}', (p) => ({ path: `/api/catalog/products/${p.slug}*` })],
  ['GET /api/catalog/filters', (p) => ({ path: `/api/catalog/filters?category=${p.category.slug}` })],
  // Cart, coupon and checkout
  [
    'POST /api/cart/items',
    (p) => ({ method: 'POST', path: '/api/cart/items', json: { variantId: p.variants[0]?.id, quantity: 1 } }),
  ],
  ['POST /api/cart/coupon', () => ({ method: 'POST', path: '/api/cart/coupon', json: { code: 'SQLMAP10' } })],
  ['GET /api/checkout/shipping-cost', () => ({ path: '/api/checkout/shipping-cost?province=SANTA_FE' })],
  [
    'POST /api/checkout',
    () => ({
      method: 'POST',
      path: '/api/checkout',
      json: { buyer: BUYER, address: ADDRESS, paymentProvider: 'MERCADOPAGO', acceptedTerms: true, saveAddress: false },
    }),
  ],
  // Buyers without an account, and sign-in
  ['GET /api/orders/{token}', () => ({ path: '/api/orders/0123456789abcdef0123456789abcdef0123456789a*' })],
  [
    'POST /api/withdrawals',
    () => ({ method: 'POST', path: '/api/withdrawals', json: { orderNumber: '1001', email: BUYER.email } }),
  ],
  ['POST /api/account/login', () => ({ method: 'POST', path: '/api/account/login', json: CREDENTIALS })],
  [
    'POST /api/account/password-recovery',
    () => ({ method: 'POST', path: '/api/account/password-recovery', json: { email: BUYER.email } }),
  ],
  [
    'POST /api/admin/auth/login',
    () => ({
      method: 'POST',
      path: '/api/admin/auth/login',
      json: { email: 'admin@pijamaparty.test', password: CREDENTIALS.password },
    }),
  ],
  // The back office's lists (typed search text, filters and dates), with the administrator's session
  ['GET /api/admin/orders', () => ({ path: '/api/admin/orders?q=ana&status=PAID&from=2026-01-01&to=2026-12-31&page=0' })],
  [
    'GET /api/admin/catalog/products',
    () => ({ path: '/api/admin/catalog/products?q=pijama&state=PUBLISHED&stock=IN_STOCK&page=0' }),
  ],
  ['GET /api/admin/catalog/stock', () => ({ path: '/api/admin/catalog/stock?filter=ALL&q=pijama&page=0' })],
  ['GET /api/admin/customers', () => ({ path: '/api/admin/customers?q=ana&from=2026-01-01&to=2026-12-31&page=0' })],
  [
    'GET /api/admin/activity',
    () => ({ path: '/api/admin/activity?area=ORDERS&from=2026-01-01&to=2026-12-31&page=0' }),
  ],
];

for (const [name, build] of TARGETS) {
  test(`sqlmap finds no injection in ${name}`, async () => {
    const verdict = await runSqlmap(port, { name, ...build(product) });

    // The report's end: what was tested and how it ended, so a clean verdict can be read, not just trusted
    console.log(`--- sqlmap, ${name}\n${verdict.output.split('\n').slice(-40).join('\n')}`);
    expect(
      verdict.kind,
      verdict.kind === 'injectable' ? 'sqlmap found an injection point' : 'sqlmap reached no verdict',
    ).toBe('clean');
  });
}
