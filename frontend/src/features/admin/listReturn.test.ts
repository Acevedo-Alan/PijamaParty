import { describe, expect, it } from 'vitest';
import { listReturn, listSearchOf } from './listReturn.ts';

describe('back to the list (UC-06, UC-18)', () => {
  it('given the state a list hands over, when read, then its filters come back', () => {
    expect(listSearchOf(listReturn('?estado=PUBLISHED&stock=LOW'))).toBe('?estado=PUBLISHED&stock=LOW');
    expect(listSearchOf(listReturn(''))).toBe('');
  });

  it('given any other state, when read, then it is no filters', () => {
    expect(listSearchOf(null)).toBe('');
    expect(listSearchOf(undefined)).toBe('');
    expect(listSearchOf('?stock=LOW')).toBe('');
    expect(listSearchOf({ listSearch: 3 })).toBe('');
    expect(listSearchOf({ listSearch: '//elsewhere.example' })).toBe('');
  });
});
