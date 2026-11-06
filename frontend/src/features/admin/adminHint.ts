/**
 * T-E8-27: a hint, kept in the browser, that this browser had a back-office session. The store reads it to decide
 * whether to ask the server who is signed in, so visitors who never opened the back office make no extra request.
 * It grants nothing: the server alone decides who is an administrator.
 */
const KEY = 'pp-admin';

export function maybeAdmin(): boolean {
  try {
    return window.localStorage.getItem(KEY) === '1';
  } catch {
    return false;
  }
}

export function markAdmin(): void {
  try {
    window.localStorage.setItem(KEY, '1');
  } catch {
    // Without storage the store simply never offers the shortcut.
  }
}

export function clearAdmin(): void {
  try {
    window.localStorage.removeItem(KEY);
  } catch {
    // Nothing to remove.
  }
}
