import { describe, expect, it } from 'vitest';
import { formatMoney } from './formatMoney.ts';

/** Intl separates the sign with a no-break space. */
function plain(text: string): string {
  return text.replace(/\s/g, ' ');
}

describe('formatMoney', () => {
  it('given a whole amount in cents, when formatted, then it shows pesos with thousands separators and no cents', () => {
    expect(plain(formatMoney(1_234_500))).toBe('$ 12.345');
  });

  it('given an amount with cents, when formatted, then it shows two decimals', () => {
    expect(plain(formatMoney(1_234_550))).toBe('$ 12.345,50');
  });
});
