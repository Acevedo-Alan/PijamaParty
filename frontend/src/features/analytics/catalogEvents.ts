import { pesos, trackEvent } from './analytics.ts';

/**
 * The catalog events of RNF-25 (T-E1-24). Products are identified by their slug: no variant, and so no SKU, is chosen
 * yet. Amounts go in pesos with currency ARS.
 */

export interface ListedForAnalytics {
  slug: string;
  name: string;
  fromPriceCents: number;
}

/** Which list the products were shown in. */
export function listId(category: string | undefined, searching: boolean): string {
  if (searching) {
    return 'search_results';
  }
  return category === undefined ? 'all_products' : `category_${category}`;
}

/** view_item_list for one page of products; {@code firstIndex} is the place of its first product in the list. */
export function trackItemList(list: string, products: readonly ListedForAnalytics[], firstIndex: number): void {
  if (products.length === 0) {
    return;
  }
  trackEvent({
    name: 'view_item_list',
    params: {
      item_list_id: list,
      item_list_name: list,
      items: products.map((product, offset) => ({
        item_id: product.slug,
        item_name: product.name,
        price: pesos(product.fromPriceCents),
        index: firstIndex + offset,
      })),
    },
  });
}

/** view_item and ViewContent for a product page. */
export function trackItemView(product: ListedForAnalytics): void {
  const value = pesos(product.fromPriceCents);
  trackEvent(
    {
      name: 'view_item',
      params: {
        currency: 'ARS',
        value,
        items: [{ item_id: product.slug, item_name: product.name, price: value }],
      },
    },
    { name: 'ViewContent', params: { content_ids: [product.slug], content_type: 'product', value, currency: 'ARS' } },
  );
}

/** A text that may be an email or a national ID is never sent (R-05). */
export function mayBePersonal(text: string): boolean {
  return text.includes('@') || /\d{7,}/.test(text.replace(/[\s.-]/g, ''));
}

/** search and Search for a text whose results are shown. */
export function trackSearch(text: string): void {
  if (mayBePersonal(text)) {
    return;
  }
  trackEvent({ name: 'search', params: { search_term: text } }, { name: 'Search', params: { search_string: text } });
}
