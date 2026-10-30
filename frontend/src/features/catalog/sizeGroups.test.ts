import { describe, expect, it } from 'vitest';
import { groupSizes } from './sizeGroups.ts';

describe('size groups of the filters panel (changes to UC-02)', () => {
  it('given mixed sizes, when grouped, then letters, numbers and the rest come apart, each in natural order', () => {
    expect(groupSizes(['10', '12', '2', 'Único', 'XL', 'S', '4', 'M', 'S/M', '18', 'L'])).toEqual([
      { kind: 'letters', sizes: ['S', 'M', 'L', 'XL'] },
      { kind: 'numbers', sizes: ['2', '4', '10', '12', '18'] },
      { kind: 'other', sizes: ['Único', 'S/M'] },
    ]);
  });

  it('given sizes of one kind, when grouped, then there is a single group', () => {
    expect(groupSizes(['L', 's', 'M'])).toEqual([{ kind: 'letters', sizes: ['s', 'M', 'L'] }]);
  });

  it('given no sizes, when grouped, then there are no groups', () => {
    expect(groupSizes([])).toEqual([]);
  });
});
