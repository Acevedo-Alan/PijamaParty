import { SIGN_IN_PATH } from './AccountLink.tsx';

/** The query key of the sign-in page with the path to go back to once signed in (T-E7-10, UC-13). */
export const RETURN_KEY = 'volver';

/** The sign-in page that comes back to {@code path}, e.g. the checkout. */
export function signInBackTo(path: string): string {
  return `${SIGN_IN_PATH}?${new URLSearchParams({ [RETURN_KEY]: path }).toString()}`;
}

/**
 * Where to go once signed in: the path given, only when it is one of this store (a single leading "/"), so the link
 * cannot send the customer to another site; "/" otherwise. "//host" and "/\host" are other sites to a browser.
 */
export function returnPathOf(value: string | null): string {
  if (value === null || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return '/';
  }
  return value;
}
