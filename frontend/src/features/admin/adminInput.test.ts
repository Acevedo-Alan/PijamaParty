import { describe, expect, it } from 'vitest';
import { centsToPesosInput, parsePesos, parseQuantity, splitList } from './adminInput.ts';

describe('price in pesos', () => {
  it.each([
    ['12500', 1_250_000],
    ['12.500', 1_250_000],
    ['1.234.567', 123_456_700],
    ['12500,5', 1_250_050],
    ['12.500,05', 1_250_005],
    ['12500.50', 1_250_050],
    [' 0 ', 0],
  ])('given "%s", when read, then it is %i cents', (text, cents) => {
    expect(parsePesos(text)).toBe(cents);
  });

  it.each(['', 'abc', '12,500,00', '12.50.0', '-5', '1,234', '12.5000', '99999999999999999'])(
    'given "%s", when read, then it is not an amount',
    (text) => {
      expect(parsePesos(text)).toBeNull();
    },
  );

  it('given cents, when shown in the field, then cents follow a comma only when there are some', () => {
    expect(centsToPesosInput(1_250_000)).toBe('12500');
    expect(centsToPesosInput(1_250_005)).toBe('12500,05');
    expect(parsePesos(centsToPesosInput(1_250_050))).toBe(1_250_050);
  });
});

describe('comma-separated list', () => {
  it('given values with spaces and empty entries, when split, then they are trimmed in order without empties', () => {
    expect(splitList(' S, M ,,L , ')).toEqual(['S', 'M', 'L']);
    expect(splitList('  ')).toEqual([]);
  });
});

describe('quantity', () => {
  it.each([
    ['5', 5],
    ['-3', -3],
    ['−3', -3],
    ['+2', 2],
    ['0', 0],
  ])('given "%s", when read, then it is %i', (text, quantity) => {
    expect(parseQuantity(text)).toBe(quantity);
  });

  it.each(['', '1.5', 'abc', '--1', '1e3'])('given "%s", when read, then it is not a whole number', (text) => {
    expect(parseQuantity(text)).toBeNull();
  });
});
