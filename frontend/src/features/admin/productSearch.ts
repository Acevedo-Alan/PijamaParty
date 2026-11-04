/** UC-06, UC-07: what the product and stock searches accept, as the server checks it. */
export const MAX_PRODUCT_SEARCH = 100;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** 2 to 100 characters of the product's name, or a SKU; blank is no search. */
export function validProductSearch(typed: string): boolean {
  // Code points, as the server counts them.
  const length = Array.from(typed.trim()).length;
  return length === 0 || (length >= 2 && length <= MAX_PRODUCT_SEARCH);
}

/** A category id as the server takes it; anything else in the address is no category. */
export function isUuid(text: string): boolean {
  return UUID.test(text);
}
