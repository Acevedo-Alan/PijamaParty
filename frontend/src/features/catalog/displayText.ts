/**
 * T-E7-71: the catalog's names and sizes read alike however they were typed in the back office. Only the text the
 * shopper reads changes; the values sent to the server (a size in the filter's URL, the variant picked) stay as stored.
 */

/**
 * Lowercase inside a name, unless they start it or follow " - ". Not the articles: in a name they start a proper noun
 * ("Los Simpsons").
 */
const MINOR_WORDS: ReadonlySet<string> = new Set([
  'y', 'e', 'o', 'u', 'a', 'al', 'de', 'del', 'en', 'con', 'sin', 'por', 'para', 'su', 'sus',
]);

/** Kept as typed: acronyms the shop's names use that would not read as words. */
const ACRONYMS: ReadonlySet<string> = new Set(['AFA', 'USB', 'ML']);

const LETTERS = /\p{L}+/gu;

function capitalize(letters: string): string {
  return letters.charAt(0).toLocaleUpperCase('es') + letters.slice(1);
}

/** One space-separated word: each run of letters in it ("Infantil/Adulto", "K-POP") is capitalized on its own. */
function titleWord(word: string, first: boolean): string {
  if (ACRONYMS.has(word.replace(/[^\p{L}]/gu, ''))) {
    return word;
  }
  const lower = word.toLocaleLowerCase('es');
  if (!first && MINOR_WORDS.has(lower)) {
    return lower;
  }
  return word.replace(LETTERS, (letters: string, at: number) => {
    // Letters touching a digit are a code ("BT21", "150x150"); not those beside it ("MINECRAFT(8)").
    if (/\d/.test(word.charAt(at - 1)) || /\d/.test(word.charAt(at + letters.length))) {
      return letters;
    }
    // A word with an inner capital ("McQueen", "LeBron") was typed so on purpose.
    return letters === letters.toLocaleUpperCase('es') || letters === letters.toLocaleLowerCase('es')
      ? capitalize(letters.toLocaleLowerCase('es'))
      : capitalize(letters);
  });
}

/** "TOALLON KUROMI" → "Toallon Kuromi"; "Top y short - LILO Y STITCH" → "Top y Short - Lilo y Stitch". */
export function displayProductName(name: string): string {
  const words = name.trim().split(/\s+/);
  return words.map((word, index) => titleWord(word, index === 0 || words[index - 1] === '-')).join(' ');
}

/** "Unico" in any case reads "Único"; a letter size ("s", "xl") is in capitals; any other stays as stored. */
export function displaySize(size: string): string {
  const key = size.normalize('NFD').replace(/\p{M}/gu, '').trim().toLowerCase();
  if (key === 'unico') {
    return 'Único';
  }
  return /^x{0,3}[sml]$/.test(key) ? key.toUpperCase() : size;
}
