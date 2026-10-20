import { STORE_CONTACT, type StoreContact } from '../features/help/storeContact.ts';

/** Fills the business's contact links (D-15) for one test; clearStoreContact empties them again. */
export function setStoreContact(contact: StoreContact): void {
  Object.assign(STORE_CONTACT, contact);
}

/** Back to the build's state: nothing sent by the business yet. */
export function clearStoreContact(): void {
  for (const key of Object.keys(STORE_CONTACT)) {
    Reflect.deleteProperty(STORE_CONTACT, key);
  }
}
