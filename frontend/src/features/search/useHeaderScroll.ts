import { useEffect, useState, type RefObject } from 'react';

/** How far the page must move in one direction before the header hides or shows, so a shaky thumb does nothing. */
const SCROLL_DELTA = 8;

export interface HeaderScroll {
  /** The page is past the header's height: its background turns translucent (T-E7-16), a pill from 860 px (T-E7-30). */
  scrolled: boolean;
  /** The visitor is scrolling down past the header: it hides below 860 px (T-E7-15). */
  hidden: boolean;
}

const AT_TOP: HeaderScroll = { scrolled: false, hidden: false };

/**
 * UC-31: how the header follows the scroll. It hides while the visitor scrolls down past it and shows as soon as they
 * scroll up; within the header's height of the top, or while a modal dialog (menu, search, cart, filters, lightbox)
 * is open, it never hides. The CSS hides it below 860 px only, so on a computer the header always stays.
 */
export function useHeaderScroll(header: RefObject<HTMLElement | null>): HeaderScroll {
  const [state, setState] = useState(AT_TOP);

  useEffect(() => {
    let last = window.scrollY;
    let frame = 0;

    // Keeps the same object when nothing changed, so the header does not render on every frame.
    const show = (scrolled: boolean, hidden: boolean) => {
      setState((current) =>
        current.scrolled === scrolled && current.hidden === hidden ? current : { scrolled, hidden },
      );
    };

    const update = () => {
      frame = 0;
      const y = window.scrollY;
      const scrolled = y > (header.current?.offsetHeight ?? 0);
      if (!scrolled || document.querySelector('dialog[open]') !== null) {
        show(scrolled, false);
        last = y;
        return;
      }
      if (y - last > SCROLL_DELTA) {
        show(true, true);
        last = y;
      } else if (last - y > SCROLL_DELTA) {
        show(true, false);
        last = y;
      } else {
        setState((current) => (current.scrolled ? current : { scrolled: true, hidden: current.hidden }));
      }
    };
    // One read per frame, whatever the number of scroll events.
    const onScroll = () => {
      if (frame === 0) {
        frame = requestAnimationFrame(update);
      }
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      cancelAnimationFrame(frame);
    };
  }, [header]);

  return state;
}
