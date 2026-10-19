import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach } from 'vitest';

// Lazy routes (the back office) load their chunk on first use, which a busy parallel run can take over a second.
configure({ asyncUtilTimeout: 5000 });

afterEach(() => {
  cleanup();
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
