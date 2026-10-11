export type ConsentChoice = 'accepted' | 'rejected';

const KEY = 'pp-consent';
/** A choice is remembered for 12 months (RNF-25), then the banner asks again. */
const VALID_MS = 365 * 24 * 60 * 60 * 1000;

interface Stored {
  choice: ConsentChoice;
  decidedAt: number;
}

function isStored(value: unknown): value is Stored {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const { choice, decidedAt } = value as Record<string, unknown>;
  return (choice === 'accepted' || choice === 'rejected') && typeof decidedAt === 'number';
}

/** The visitor's choice, or null when there is none, it is older than 12 months or it cannot be read. */
export function readConsent(now: number = Date.now(), storage: Storage = localStorage): ConsentChoice | null {
  try {
    const raw = storage.getItem(KEY);
    const parsed: unknown = raw === null ? null : JSON.parse(raw);
    if (!isStored(parsed) || parsed.decidedAt > now || now - parsed.decidedAt > VALID_MS) {
      return null;
    }
    return parsed.choice;
  } catch {
    return null;
  }
}

/** Remembers the choice from now; a browser that refuses storage only keeps it for this page. */
export function saveConsent(choice: ConsentChoice, now: number = Date.now(), storage: Storage = localStorage): void {
  try {
    storage.setItem(KEY, JSON.stringify({ choice, decidedAt: now } satisfies Stored));
  } catch {
    // Private mode or full storage: the banner will ask again next time.
  }
}
