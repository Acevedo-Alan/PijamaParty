import { pesos, trackEvent } from './analytics.ts';

/** The cart events of RNF-25 (T-E2-12). A variant is chosen, so the item is identified by its SKU. */

export interface AddedForAnalytics {
  sku: string;
  name: string;
  size: string;
  color: string;
  /** The server's price of the added item, from the cart of the answer (R-01). */
  unitPriceCents: number;
  quantity: number;
}

/** add_to_cart and AddToCart for a successful add (UC-08). */
export function trackAddToCart(added: AddedForAnalytics): void {
  const price = pesos(added.unitPriceCents);
  const value = pesos(added.unitPriceCents * added.quantity);
  trackEvent(
    {
      name: 'add_to_cart',
      params: {
        currency: 'ARS',
        value,
        items: [
          {
            item_id: added.sku,
            item_name: added.name,
            item_variant: `${added.color} / ${added.size}`,
            price,
            quantity: added.quantity,
          },
        ],
      },
    },
    {
      name: 'AddToCart',
      params: {
        content_ids: [added.sku],
        content_type: 'product',
        contents: [{ id: added.sku, quantity: added.quantity }],
        value,
        currency: 'ARS',
      },
    },
  );
}
