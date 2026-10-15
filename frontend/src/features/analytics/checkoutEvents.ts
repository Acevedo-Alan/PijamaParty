import { analyticsLoaded, pesos, trackEvent } from './analytics.ts';

/**
 * The checkout events of RNF-25 (T-E3-20). Items are identified by their SKU and amounts are the server's, in pesos
 * with currency ARS. Nothing of the buyer is sent (R-05).
 */

export interface ItemForAnalytics {
  sku: string;
  name: string;
  size: string;
  color: string;
  unitPriceCents: number;
  quantity: number;
}

/** The cart being bought: its available items and its total before shipping. */
export interface CheckoutForAnalytics {
  items: readonly ItemForAnalytics[];
  totalCents: number;
  /** The code of an applicable coupon, otherwise null. */
  couponCode: string | null;
}

export interface PaidOrderForAnalytics {
  number: number;
  items: readonly ItemForAnalytics[];
  totalCents: number;
  shippingCents: number;
  couponCode: string | null;
}

function gaItems(items: readonly ItemForAnalytics[]) {
  return items.map((item) => ({
    item_id: item.sku,
    item_name: item.name,
    item_variant: `${item.color} / ${item.size}`,
    price: pesos(item.unitPriceCents),
    quantity: item.quantity,
  }));
}

function pixelContents(items: readonly ItemForAnalytics[]) {
  return {
    content_ids: items.map((item) => item.sku),
    content_type: 'product',
    contents: items.map((item) => ({ id: item.sku, quantity: item.quantity })),
  };
}

function units(items: readonly ItemForAnalytics[]): number {
  return items.reduce((sum, item) => sum + item.quantity, 0);
}

function coupon(code: string | null) {
  return code === null ? {} : { coupon: code };
}

/** begin_checkout and InitiateCheckout, once per visit to the checkout (UC-13). */
export function trackBeginCheckout(checkout: CheckoutForAnalytics): void {
  const value = pesos(checkout.totalCents);
  trackEvent(
    {
      name: 'begin_checkout',
      params: { currency: 'ARS', value, ...coupon(checkout.couponCode), items: gaItems(checkout.items) },
    },
    {
      name: 'InitiateCheckout',
      params: { ...pixelContents(checkout.items), num_items: units(checkout.items), value, currency: 'ARS' },
    },
  );
}

/** add_payment_info and AddPaymentInfo, the first time in the visit the buyer goes on from the payment step (UC-13). */
export function trackAddPaymentInfo(checkout: CheckoutForAnalytics, paymentType: string): void {
  const value = pesos(checkout.totalCents);
  trackEvent(
    {
      name: 'add_payment_info',
      params: {
        currency: 'ARS',
        value,
        ...coupon(checkout.couponCode),
        payment_type: paymentType,
        items: gaItems(checkout.items),
      },
    },
    { name: 'AddPaymentInfo', params: { ...pixelContents(checkout.items), value, currency: 'ARS' } },
  );
}

const PURCHASES_KEY = 'pp-purchases';
/** The orders whose purchase this browser remembers having sent; older marks are dropped. */
const KEPT_PURCHASES = 50;

/** The browser's storage, or null where even reaching it fails (blocked site data). */
function browserStorage(): Storage | null {
  try {
    return localStorage;
  } catch {
    return null;
  }
}

function sentPurchases(storage: Storage | null): number[] {
  if (storage === null) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(storage.getItem(PURCHASES_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((each): each is number => typeof each === 'number') : [];
  } catch {
    return [];
  }
}

function markPurchaseSent(number: number, storage: Storage | null): void {
  if (storage === null) {
    return;
  }
  try {
    const kept = [...sentPurchases(storage).filter((each) => each !== number), number].slice(-KEPT_PURCHASES);
    storage.setItem(PURCHASES_KEY, JSON.stringify(kept));
  } catch {
    // Private mode or full storage: a reload may send it again, and both tools drop it by its ids.
  }
}

/**
 * purchase and Purchase for a paid order (UC-14), once per order in this browser: a mark per order number keeps a
 * reload or a later visit from sending it again. Without the tools loaded nothing is sent and nothing is marked.
 */
export function trackPurchase(order: PaidOrderForAnalytics, storage: Storage | null = browserStorage()): void {
  if (!analyticsLoaded() || sentPurchases(storage).includes(order.number)) {
    return;
  }
  const value = pesos(order.totalCents);
  const eventId = `purchase-${String(order.number)}`;
  trackEvent(
    {
      name: 'purchase',
      params: {
        transaction_id: String(order.number),
        event_id: eventId,
        currency: 'ARS',
        value,
        shipping: pesos(order.shippingCents),
        ...coupon(order.couponCode),
        items: gaItems(order.items),
      },
    },
    {
      name: 'Purchase',
      params: { ...pixelContents(order.items), num_items: units(order.items), value, currency: 'ARS' },
      eventId,
    },
  );
  markPurchaseSent(order.number, storage);
}
