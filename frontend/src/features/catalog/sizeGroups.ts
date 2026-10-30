/** How the filters panel sorts a size: by letter (S, M, L), by number (2, 4, 16) or neither (Único, S/M). */
export type SizeKind = 'letters' | 'numbers' | 'other';

export interface SizeGroup {
  kind: SizeKind;
  sizes: string[];
}

const LETTERS = ['XXS', 'XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL'];
const NUMBER = /^\d{1,3}$/;

function kindOf(size: string): SizeKind {
  if (NUMBER.test(size)) {
    return 'numbers';
  }
  return LETTERS.includes(size.toUpperCase()) ? 'letters' : 'other';
}

const RANK: Record<SizeKind, (size: string) => number> = {
  letters: (size) => LETTERS.indexOf(size.toUpperCase()),
  numbers: Number,
  // Neither: the order the API gave (UC-02), which a stable sort keeps.
  other: () => 0,
};

const KIND_ORDER: readonly SizeKind[] = ['letters', 'numbers', 'other'];

/**
 * The sizes of the panel in groups, each in its natural order (2, 4, 16 rather than 16, 2, 4), letters first; a kind
 * without sizes is left out. The products of a shop mix adult letters, kids' numbers and one-size items, so a single
 * list in the API's order reads as unordered (changes to UC-02).
 */
export function groupSizes(sizes: readonly string[]): SizeGroup[] {
  return KIND_ORDER.flatMap((kind) => {
    const ofKind = sizes.filter((size) => kindOf(size) === kind).sort((a, b) => RANK[kind](a) - RANK[kind](b));
    return ofKind.length === 0 ? [] : [{ kind, sizes: ofKind }];
  });
}
