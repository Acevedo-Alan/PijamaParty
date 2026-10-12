import type { components } from '../api/schema.d.ts';
import { answer } from './catalogApi.ts';

export type CartBody = components['schemas']['CartResponse'];
export type CartItemBody = components['schemas']['ItemResponse'];

/** A pink M pijama in stock at ARS 25,000, with its photo. */
export function cartItem(overrides: Partial<CartItemBody> = {}): CartItemBody {
  return {
    id: 'item-1',
    variantId: 'M-Rosa',
    quantity: 1,
    unitPriceCents: 2_500_000,
    previousUnitPriceCents: null,
    status: 'AVAILABLE',
    available: 3,
    product: {
      name: 'Pijama Luna',
      slug: 'luna',
      size: 'M',
      color: 'Rosa',
      image: {
        placeholder: 'data:image/webp;base64,AAAA',
        files: [
          { width: 400, url: '/uploads/luna-rosa-400.webp' },
          { width: 800, url: '/uploads/luna-rosa-800.webp' },
          { width: 1200, url: '/uploads/luna-rosa-1200.webp' },
        ],
      },
    },
    ...overrides,
  };
}

export type CouponBody = components['schemas']['CouponResponse'];

/** A cart of these items, with the totals the server would send for them and this discount and coupon. */
export function cartBody(items: CartItemBody[], discountCents = 0, coupon: CouponBody | null = null): CartBody {
  const subtotalCents = items
    .filter((item) => item.status === 'AVAILABLE')
    .reduce((sum, item) => sum + (item.unitPriceCents ?? 0) * (item.quantity ?? 0), 0);
  return { items, coupon: coupon as never, subtotalCents, discountCents, totalCents: subtotalCents - discountCents };
}

export function cart(items: CartItemBody[], discountCents = 0, coupon: CouponBody | null = null) {
  return answer(200, cartBody(items, discountCents, coupon));
}
