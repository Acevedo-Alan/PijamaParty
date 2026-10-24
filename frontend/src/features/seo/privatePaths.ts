/**
 * T-E6-23 (RNF-31.1): the storefront's paths search engines must not index, the same ones robots.txt disallows in the
 * Caddyfile: the account, checkout, tracking links and search results (thin duplicates of the listing). The back
 * office adds its own noindex.
 */
export const PRIVATE_PREFIXES = ['/cuenta', '/checkout', '/pedido', '/buscar'] as const;

export function isPrivatePath(pathname: string): boolean {
  return PRIVATE_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}
