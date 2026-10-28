/** UC-31: the pages of the purchase, whose header leaves out the navigation, the menu and the search. */
const PURCHASE_PREFIXES = ['/checkout', '/pedido'] as const;

export function isPurchasePath(pathname: string): boolean {
  return PURCHASE_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}
