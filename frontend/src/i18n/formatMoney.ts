const whole = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 });
const withCents = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 2 });

/** An amount in cents (the API's Money) as Argentine pesos; cents are shown only when there are some. */
export function formatMoney(cents: number): string {
  return cents % 100 === 0 ? whole.format(cents / 100) : withCents.format(cents / 100);
}
