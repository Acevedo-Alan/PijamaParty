import { isEmail } from './checkoutForm.ts';

/** The common domains in Argentina, in the order that breaks a tie (UC-13, T-E3-24). */
const COMMON_DOMAINS: readonly string[] = [
  'gmail.com',
  'hotmail.com',
  'hotmail.com.ar',
  'outlook.com',
  'outlook.com.ar',
  'yahoo.com',
  'yahoo.com.ar',
  'live.com',
  'icloud.com',
];

const MAX_EDITS = 2;

/**
 * Edits from one text to the other: insertions, deletions, substitutions and swaps of two adjacent letters
 * (optimal string alignment).
 */
function editsBetween(from: string, to: string): number {
  const rows = Array.from({ length: from.length + 1 }, (_, i) =>
    Array.from({ length: to.length + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0)),
  );
  const at = (i: number, j: number) => rows[i]?.[j] ?? Infinity;
  for (let i = 1; i <= from.length; i++) {
    const row = rows[i] ?? [];
    for (let j = 1; j <= to.length; j++) {
      const cost = from[i - 1] === to[j - 1] ? 0 : 1;
      let edits = Math.min(at(i - 1, j) + 1, at(i, j - 1) + 1, at(i - 1, j - 1) + cost);
      if (i > 1 && j > 1 && from[i - 1] === to[j - 2] && from[i - 2] === to[j - 1]) {
        edits = Math.min(edits, at(i - 2, j - 2) + 1);
      }
      row[j] = edits;
    }
  }
  return at(from.length, to.length);
}

/**
 * The email with a common domain in place of a misspelled one, such as `ana@gmail.com` for `ana@gmial.com`; null
 * for a common domain, one too far from all of them, or an email that breaks the rule of UC-13.
 */
export function suggestEmail(value: string): string | null {
  if (!isEmail(value)) {
    return null;
  }
  const email = value.trim().toLowerCase();
  const at = email.indexOf('@');
  const local = email.slice(0, at);
  const domain = email.slice(at + 1);
  if (COMMON_DOMAINS.includes(domain)) {
    return null;
  }
  let closest: { domain: string; edits: number } | null = null;
  for (const common of COMMON_DOMAINS) {
    const edits = editsBetween(domain, common);
    if (edits <= MAX_EDITS && (closest === null || edits < closest.edits)) {
      closest = { domain: common, edits };
    }
  }
  return closest === null ? null : `${local}@${closest.domain}`;
}
