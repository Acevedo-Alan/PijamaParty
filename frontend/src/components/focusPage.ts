/** The page's heading, or its main region while the heading is not there yet. */
function pageTarget(): HTMLElement | null {
  return document.querySelector<HTMLElement>('main h1') ?? document.querySelector<HTMLElement>('main');
}

/**
 * WCAG 2.4.3: puts the focus on the page (its heading, else its main region), without scrolling: after a navigation,
 * the skip link, or a control that goes away when used (the consent banner, T-E6-25).
 */
export function focusPage(): void {
  const target = pageTarget();
  if (target !== null) {
    target.tabIndex = -1;
    target.focus({ preventScroll: true });
  }
}
