/**
 * A back-office call answered 401 after the session expired (60 minutes without activity, UC-04). The admin layout
 * listens and opens the sign-in page; the call itself fails with {@link SessionExpiredError}.
 */
type Listener = () => void;

const listeners = new Set<Listener>();

export class SessionExpiredError extends Error {
  constructor() {
    super('The back-office session expired');
    this.name = 'SessionExpiredError';
  }
}

export function onSessionExpired(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** Throws on a 401 after telling the listeners, so every back-office call handles an expired session alike. */
export function ensureSignedIn(response: Response): void {
  if (response.status === 401) {
    for (const listener of listeners) {
      listener();
    }
    throw new SessionExpiredError();
  }
}

/** The extra properties of a problem+json answer (slug, fields, problem, onHand, lockedUntil). */
export function problemProperty(error: unknown, name: string): unknown {
  return typeof error === 'object' && error !== null ? (error as Record<string, unknown>)[name] : undefined;
}

/** A new Idempotency-Key (UC-06 photos, UC-07 adjustments). */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

/** A text property of a problem+json answer, or an empty text without it. */
export function problemText(error: unknown, name: string): string {
  const value = problemProperty(error, name);
  return typeof value === 'string' ? value : '';
}
