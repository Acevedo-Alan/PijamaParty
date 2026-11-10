import { describe, expect, it } from 'vitest';
import { isSakuraSeason } from './sakuraSeason.ts';

describe('sakura season (T-E7-56)', () => {
  it.each([
    ['15 October', '2026-10-15T12:00:00-03:00', true],
    ['1 September at midnight in Argentina', '2026-09-01T00:00:00-03:00', true],
    ['30 November at 23:30 in Argentina (1 December in UTC)', '2026-11-30T23:30:00-03:00', true],
    ['31 August at 23:30 in Argentina (1 September in UTC)', '2026-08-31T23:30:00-03:00', false],
    ['1 December', '2026-12-01T00:00:00-03:00', false],
    ['15 March', '2026-03-15T12:00:00-03:00', false],
  ])('given %s (%s), when the season is read, then spring is %s', (_, instant, expected) => {
    expect(isSakuraSeason(new Date(instant))).toBe(expected);
  });
});
