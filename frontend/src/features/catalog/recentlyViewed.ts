/**
 * The slugs of the products this browser opened, newest first (T-E7-33). Only slugs: no personal data, and never sent
 * to the server or to analytics. Every read and write tolerates a browser that refuses its storage.
 */
const RECENT_KEY = 'pp-recently-viewed';
/** How many products the browser remembers. */
const KEPT = 8;

/** The browser's storage, or null where even reaching it fails (blocked site data). */
function browserStorage(): Storage | null {
  try {
    return localStorage;
  } catch {
    return null;
  }
}

export function readRecentlyViewed(): string[] {
  try {
    const parsed: unknown = JSON.parse(browserStorage()?.getItem(RECENT_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((slug): slug is string => typeof slug === 'string') : [];
  } catch {
    return [];
  }
}

function write(slugs: string[]) {
  try {
    browserStorage()?.setItem(RECENT_KEY, JSON.stringify(slugs.slice(0, KEPT)));
  } catch {
    // Full or refused: the list is a convenience, the page works without it.
  }
}

/** A product page loaded: its product goes first, once. */
export function rememberViewed(slug: string) {
  write([slug, ...readRecentlyViewed().filter((viewed) => viewed !== slug)]);
}

/** A product no longer listed is forgotten. */
export function forgetViewed(slug: string) {
  write(readRecentlyViewed().filter((viewed) => viewed !== slug));
}
