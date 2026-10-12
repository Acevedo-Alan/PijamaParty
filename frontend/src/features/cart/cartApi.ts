import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { toImage, type ListingImage } from '../catalog/catalogApi.ts';

type CartResponse = components['schemas']['CartResponse'];
type ItemResponse = components['schemas']['ItemResponse'];
type ItemProductResponse = components['schemas']['ItemProductResponse'];

/** Whether an item can be bought now (UC-09): only AVAILABLE items count in the subtotal. */
export type ItemStatus = NonNullable<ItemResponse['status']>;

/** What the panel shows of an item's variant, also when the shop no longer sells it (UC-08). */
export interface CartProduct {
  name: string;
  slug: string;
  size: string;
  color: string;
  /** The first photo of its color, or else of the whole product; null without one. */
  image: ListingImage | null;
}

export interface CartItem {
  id: string;
  variantId: string;
  quantity: number;
  unitPriceCents: number;
  /** The last price the customer saw when the current one differs (UC-09), otherwise null. */
  previousUnitPriceCents: number | null;
  status: ItemStatus;
  available: number;
  /** Null only for a variant the catalog does not have. */
  product: CartProduct | null;
}

/** The cart as the server prices it (R-01, R-02): the totals are never calculated here. */
export interface Cart {
  items: CartItem[];
  subtotalCents: number;
  discountCents: number;
  totalCents: number;
}

export const EMPTY_CART: Cart = { items: [], subtotalCents: 0, discountCents: 0, totalCents: 0 };

/** The number the cart button shows: the units in the cart, not the lines. */
export function unitCount(cart: Cart): number {
  return cart.items.reduce((units, item) => units + item.quantity, 0);
}

function toProduct(product: ItemProductResponse | null | undefined): CartProduct | null {
  if (product === null || product === undefined) {
    return null;
  }
  const { name, slug, size, color } = product;
  if (name === undefined || slug === undefined || size === undefined || color === undefined) {
    throw new Error('Incomplete product in a cart item');
  }
  return { name, slug, size, color, image: toImage(product.image) };
}

function toItem(item: ItemResponse): CartItem {
  const { id, variantId, quantity, unitPriceCents, status, available } = item;
  if (
    id === undefined ||
    variantId === undefined ||
    quantity === undefined ||
    unitPriceCents === undefined ||
    status === undefined ||
    available === undefined
  ) {
    throw new Error('Incomplete cart item');
  }
  return {
    id,
    variantId,
    quantity,
    unitPriceCents,
    previousUnitPriceCents: item.previousUnitPriceCents ?? null,
    status,
    available,
    product: toProduct(item.product),
  };
}

function toCart(data: CartResponse): Cart {
  const { subtotalCents, discountCents, totalCents } = data;
  if (subtotalCents === undefined || discountCents === undefined || totalCents === undefined) {
    throw new Error('Incomplete cart');
  }
  return { items: (data.items ?? []).map(toItem), subtotalCents, discountCents, totalCents };
}

/** GET /api/cart: the actor's cart, empty when there is none. It never creates one. */
export async function fetchCart(signal?: AbortSignal): Promise<Cart> {
  const { data, response } = await api.GET('/api/cart', { signal: signal ?? null });
  if (data === undefined) {
    throw new Error(`Cart failed with status ${String(response.status)}`);
  }
  return toCart(data);
}

/** POST /api/cart/items. Not enough stock or a variant the shop no longer sells are expected answers. */
export type AddAnswer =
  | { status: 'added'; cart: Cart }
  | { status: 'insufficientStock'; available: number }
  | { status: 'unavailable' };

function availableIn(problem: unknown): number {
  const available = typeof problem === 'object' && problem !== null ? (problem as Record<string, unknown>).available : 0;
  return typeof available === 'number' ? available : 0;
}

export async function addToCart(variantId: string, quantity: number): Promise<AddAnswer> {
  const { data, error, response } = await api.POST('/api/cart/items', { body: { variantId, quantity } });
  if (data !== undefined) {
    return { status: 'added', cart: toCart(data) };
  }
  if (response.status === 409) {
    return { status: 'insufficientStock', available: availableIn(error) };
  }
  if (response.status === 404) {
    return { status: 'unavailable' };
  }
  throw new Error(`Adding to the cart failed with status ${String(response.status)}`);
}

/** PATCH and DELETE of an item (UC-09). Not enough stock and an item no longer in the cart are expected answers. */
export type ChangeAnswer =
  | { status: 'changed'; cart: Cart }
  | { status: 'insufficientStock'; available: number }
  | { status: 'notInCart' };

function toChangeAnswer(data: CartResponse | undefined, error: unknown, response: Response): ChangeAnswer {
  if (data !== undefined) {
    return { status: 'changed', cart: toCart(data) };
  }
  if (response.status === 409) {
    return { status: 'insufficientStock', available: availableIn(error) };
  }
  if (response.status === 404) {
    return { status: 'notInCart' };
  }
  throw new Error(`Changing the cart failed with status ${String(response.status)}`);
}

/** PATCH /api/cart/items/{id}: quantity 0 removes the item without checking stock. */
export async function changeQuantity(itemId: string, quantity: number): Promise<ChangeAnswer> {
  const { data, error, response } = await api.PATCH('/api/cart/items/{id}', {
    params: { path: { id: itemId } },
    body: { quantity },
  });
  return toChangeAnswer(data, error, response);
}

/** DELETE /api/cart/items/{id}. */
export async function removeItem(itemId: string): Promise<ChangeAnswer> {
  const { data, error, response } = await api.DELETE('/api/cart/items/{id}', { params: { path: { id: itemId } } });
  return toChangeAnswer(data, error, response);
}

/** POST /api/cart/acknowledge-prices: the customer saw the current prices, so no item announces a change any more. */
export async function acknowledgePrices(): Promise<Cart> {
  const { data, response } = await api.POST('/api/cart/acknowledge-prices');
  if (data === undefined) {
    throw new Error(`Acknowledging the prices failed with status ${String(response.status)}`);
  }
  return toCart(data);
}
