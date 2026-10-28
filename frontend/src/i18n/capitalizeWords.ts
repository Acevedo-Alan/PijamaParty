/** Joining words of Spanish names and places, kept lowercase unless they open the text ("San José de la Esquina"). */
const JOINING_WORDS: ReadonlySet<string> = new Set(['de', 'del', 'la', 'las', 'los', 'y']);

/**
 * UC-13, UC-14 (E7): a name or a city as the buyer typed it, with the first letter of each word in uppercase:
 * "santa fe" → "Santa Fe", "maría-josé" → "María-José". The rest of each word stays as typed; only for showing it.
 */
export function capitalizeWords(text: string): string {
  let words = 0;
  return text
    .split(/(\s+|-)/)
    .map((part) => {
      if (part === '' || /^(\s+|-)$/.test(part)) {
        return part;
      }
      words += 1;
      if (words > 1 && JOINING_WORDS.has(part.toLocaleLowerCase('es'))) {
        return part.toLocaleLowerCase('es');
      }
      return part.charAt(0).toLocaleUpperCase('es') + part.slice(1);
    })
    .join('');
}
