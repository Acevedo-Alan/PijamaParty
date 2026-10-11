import { describe, expect, it } from 'vitest';
import { formatDateTime, formatTime } from './formatDate.ts';

describe('dates in Argentina time', () => {
  it('given an instant, when shown, then it is in Argentina time on a 24-hour clock', () => {
    const instant = new Date('2026-10-11T17:30:00Z');

    expect(formatTime(instant)).toBe('14:30');
    expect(formatDateTime(instant)).toMatch(/^11\/10\/2?0?26,? 14:30$/);
  });
});
