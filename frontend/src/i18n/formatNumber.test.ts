import { describe, expect, it } from 'vitest';
import { formatNumber } from './formatNumber.ts';

describe('formatNumber', () => {
  it('given a thousand or more, when formatted, then the thousands are separated with a dot', () => {
    expect(formatNumber(1234)).toBe('1.234');
    expect(formatNumber(1234567)).toBe('1.234.567');
  });

  it('given fewer than a thousand, when formatted, then it has no separator', () => {
    expect(formatNumber(999)).toBe('999');
    expect(formatNumber(0)).toBe('0');
  });
});
