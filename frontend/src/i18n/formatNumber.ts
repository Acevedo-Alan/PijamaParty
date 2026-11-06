const number = new Intl.NumberFormat('es-AR');

/** A count or quantity the way Argentina writes it, "1.234" (thousands with a dot). Money has `formatMoney`. */
export function formatNumber(value: number): string {
  return number.format(value);
}
