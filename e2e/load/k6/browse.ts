import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import type { Options } from 'k6/options';
import { Shopper, shopperIp } from './shopper.ts';

/**
 * T-E6-14 (RNF-16): 50 shoppers browse at the same time, each from its own IP: the home page, a category with its
 * filters, a product with its reviews and the shipping costs, and now and then a search. The p95 of the catalog
 * requests must stay under 400 ms. Every shopper waits 1 to 3 seconds between pages, as a person does.
 */

const SHOPPERS = Number(__ENV.SHOPPERS ?? '50');
const DURATION = __ENV.DURATION ?? '2m';
const IP_BASE = 1_000;

export const options: Options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: SHOPPERS },
        { duration: DURATION, target: SHOPPERS },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_duration{kind:catalog}': ['p(95)<400'],
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

interface Catalog {
  categories: string[];
  products: string[];
}

export function setup(): Catalog {
  const shopper = Shopper.at(shopperIp(IP_BASE));
  const categories = (shopper.get('/api/catalog/categories').json('items') as { slug: string }[]).map((c) => c.slug);
  const products = (shopper.get('/api/catalog/products').json('items') as { slug: string }[]).map((p) => p.slug);
  if (categories.length === 0 || products.length === 0) {
    fail('The catalog is empty: the load test seeds it first (load/load.spec.ts)');
  }
  return { categories, products };
}

function pick<T>(items: T[]): T {
  return items[Math.floor(Math.random() * items.length)] as T;
}

function ok(answer: { status: number }, page: string): void {
  check(answer, { [`${page} answers 200`]: (a) => a.status === 200 });
}

export default function browse(catalog: Catalog): void {
  const shopper = Shopper.at(shopperIp(IP_BASE + exec.vu.idInTest));
  const catalogTags = (name: string) => ({ kind: 'catalog', name });

  // The home page: the categories and the newest products.
  ok(shopper.get('/api/catalog/categories', catalogTags('categories')), 'categories');
  ok(shopper.get('/api/catalog/products', catalogTags('products')), 'home');
  sleep(1 + Math.random() * 2);

  // A category listing with its filters, and its second page now and then.
  const category = pick(catalog.categories);
  ok(shopper.get(`/api/catalog/products?category=${category}`, catalogTags('listing')), 'listing');
  ok(shopper.get(`/api/catalog/filters?category=${category}`, catalogTags('filters')), 'filters');
  if (Math.random() < 0.3) {
    ok(shopper.get(`/api/catalog/products?category=${category}&sort=PRICE_ASC`, catalogTags('listing')), 'sorted');
  }
  sleep(1 + Math.random() * 2);

  // A product page: the product, its reviews and the shipping costs of its purchase summary (RNF-31.6).
  const product = pick(catalog.products);
  ok(shopper.get(`/api/catalog/products/${product}`, catalogTags('product')), 'product');
  ok(shopper.get(`/api/catalog/products/${product}/reviews`, catalogTags('reviews')), 'reviews');
  ok(shopper.get('/api/shipping/rates', { kind: 'purchase-info', name: 'shipping-rates' }), 'shipping rates');
  sleep(1 + Math.random() * 2);

  // One shopper in four searches: the suggestions while typing, then the results.
  if (Math.random() < 0.25) {
    ok(shopper.get('/api/catalog/suggestions?q=pij', catalogTags('suggestions')), 'suggestions');
    ok(shopper.get('/api/catalog/products?q=pijama', catalogTags('search')), 'search');
    sleep(1 + Math.random() * 2);
  }
}
