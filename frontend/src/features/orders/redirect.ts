/** Leaves the store for the payment provider's checkout. Its own module so tests can watch it. */
export function goToPayment(paymentUrl: string): void {
  window.location.assign(paymentUrl);
}
