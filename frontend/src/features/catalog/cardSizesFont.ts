import { useEffect, type RefObject } from 'react';

/** How close to the screen a card's sizes come before Open Sans is fetched, so it is usually there when they show. */
const NEAR_SCREEN = '300px 0px';

let requested = false;

/** Open Sans 500 for the card sizes (T-E7-35): its stylesheet, once per page load. */
function loadCardSizesFont(): void {
  if (requested) {
    return;
  }
  requested = true;
  void import('@fontsource/open-sans/500.css');
}

/**
 * Loads Open Sans once these sizes come near the screen, not in the first load (RNF-11): on the home page the grid
 * starts below the first screen, so the font never competes with the first photo. Until it arrives the sizes show in
 * the body font, and the swap keeps the row's height. Without IntersectionObserver it loads at once.
 */
export function useCardSizesFont(sizes: RefObject<HTMLElement | null>): void {
  useEffect(() => {
    const element = sizes.current;
    if (element === null || requested) {
      return undefined;
    }
    if (typeof IntersectionObserver === 'undefined') {
      loadCardSizesFont();
      return undefined;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          loadCardSizesFont();
          observer.disconnect();
        }
      },
      { rootMargin: NEAR_SCREEN },
    );
    observer.observe(element);
    return () => {
      observer.disconnect();
    };
  }, [sizes]);
}
