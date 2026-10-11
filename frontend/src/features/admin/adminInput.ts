const CENTS_PER_PESO = 100;

/** Whole pesos with dots between thousands, or without them; optional cents after a comma ("12.500,50"). */
const ARGENTINE_AMOUNT = /^(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?$/;
/** Also accepted: a dot before 1 or 2 decimals, as a computer keyboard may type it ("12500.5"). */
const DOT_DECIMAL_AMOUNT = /^(\d+)\.(\d{1,2})$/;

/**
 * A price typed in pesos as cents, or null when it is not an amount. The range (1 to 1,000,000,000 cents) is the
 * server's rule (UC-06), so it is not checked here.
 */
export function parsePesos(text: string): number | null {
  const trimmed = text.trim();
  const match = ARGENTINE_AMOUNT.exec(trimmed) ?? DOT_DECIMAL_AMOUNT.exec(trimmed);
  if (match === null) {
    return null;
  }
  const [, whole = '', fraction = ''] = match;
  const cents = Number(whole.replaceAll('.', '')) * CENTS_PER_PESO + Number(fraction.padEnd(2, '0'));
  return Number.isSafeInteger(cents) ? cents : null;
}

/** Cents as the price field shows them: whole pesos, and cents after a comma only when there are some. */
export function centsToPesosInput(cents: number): string {
  const whole = String(Math.floor(cents / CENTS_PER_PESO));
  const fraction = cents % CENTS_PER_PESO;
  return fraction === 0 ? whole : `${whole},${String(fraction).padStart(2, '0')}`;
}

/** Sizes or colors typed separated by commas, trimmed and without empty entries, in the order typed. */
export function splitList(text: string): string[] {
  return text
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value !== '');
}

/** A whole number of units, signed; the typographic minus of − buttons also counts. Null when it is not one. */
export function parseQuantity(text: string): number | null {
  const normalized = text.trim().replace('−', '-');
  return /^[-+]?\d{1,9}$/.test(normalized) ? Number(normalized) : null;
}
