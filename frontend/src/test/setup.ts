import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { clearStoreContact } from './storeContact.ts';

// Lazy routes (the back office) load their chunk on first use, which a busy parallel run can take over a second.
configure({ asyncUtilTimeout: 5000 });

// The store's contact data (UC-36) comes from test/storeContact.ts instead of the API, so the many tests that stub
// the API path by path need not know about it. storeContactApi.test.ts checks the real request.
vi.mock('../features/help/storeContactApi.ts', async () => {
  const { storedContact } = await import('./storeContact.ts');
  return { fetchStoreContact: () => Promise.resolve(storedContact()) };
});

// Every test starts without the store's contact data; one that needs it sets it (setStoreContact).
beforeEach(() => {
  clearStoreContact();
});

afterEach(() => {
  cleanup();
  // What a test stored (consent, "Vistos recientemente") never leaks into the next one.
  localStorage.clear();
});

// jsdom has no modal dialogs (no top layer): opening and closing only toggle the open attribute.
HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
  this.setAttribute('open', '');
};
// As in browsers, closing a closed dialog does nothing.
HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
  if (!this.open) {
    return;
  }
  this.removeAttribute('open');
  this.dispatchEvent(new Event('close'));
};

// jsdom does not implement scrolling the window: a page change scrolls to the top (tests spy on it).
window.scrollTo = vi.fn();

// jsdom has no layout: scrolling an element into view does nothing (tests spy on it).
Element.prototype.scrollIntoView = function scrollIntoView() {
  // Nothing to scroll.
};
