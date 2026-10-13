const KEY = 'pijamaparty.checkout.trackingToken';

/**
 * The tracking token of the last order placed in this tab, so the return from MercadoPago finds its order: the token
 * never goes to the provider (T-E3-06). sessionStorage lives as long as the tab; a browser that refuses it only
 * loses the redirect, and the confirmation email still carries a tracking link.
 */
export function keepTrackingToken(token: string): void {
  try {
    sessionStorage.setItem(KEY, token);
  } catch {
    // Storage blocked or full: the result page then says the confirmation arrives by email.
  }
}

export function keptTrackingToken(): string | null {
  try {
    return sessionStorage.getItem(KEY);
  } catch {
    return null;
  }
}
