import type { StoreContact } from '../features/help/storeContactApi.ts';

/**
 * What GET /api/store/contact answers in tests: setup.ts replaces fetchStoreContact with this value, so a test sets the
 * business's contact data (UC-36) before rendering without stubbing the whole API.
 */
let current: StoreContact = {};

/** Fills the store's contact data for one test; clearStoreContact empties it again. */
export function setStoreContact(contact: StoreContact): void {
  current = { ...contact };
}

/** Back to a store without contact data. */
export function clearStoreContact(): void {
  current = {};
}

/** The contact data the mocked request answers. */
export function storedContact(): StoreContact {
  return current;
}
