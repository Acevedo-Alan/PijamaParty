import { AnimatePresence, m } from 'motion/react';
import { useEffect, useState, type ReactNode, type RefObject } from 'react';
import { useActionMotion } from '../../components/motion/useActionMotion.ts';
import { duration, ease } from '../../styles/motion.ts';

interface StickyAddToCartProps {
  /** The main add button: the bar shows while it is scrolled above the screen. */
  target: RefObject<HTMLElement | null>;
  children: ReactNode;
}

/**
 * E7, on phones: once the add button scrolls off the top of the screen, a bar fixed at the bottom repeats the price
 * and the button; it slides away when the button is back. From 860 px the info column stays in view and CSS hides
 * the bar.
 */
export function StickyAddToCart({ target, children }: StickyAddToCartProps) {
  const [shown, setShown] = useState(false);
  const motion = useActionMotion({
    hidden: { y: '100%' },
    shown: { y: 0 },
    transition: { duration: duration.panel, ease: ease.standard },
  });

  useEffect(() => {
    const element = target.current;
    if (element === null || typeof IntersectionObserver === 'undefined') {
      return;
    }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry !== undefined) {
        // Below the screen the button is still ahead of the visitor: only one scrolled past is missed.
        setShown(!entry.isIntersecting && entry.boundingClientRect.top < 0);
      }
    });
    observer.observe(element);
    return () => {
      observer.disconnect();
    };
  }, [target]);

  return (
    <AnimatePresence>
      {shown && (
        <m.div key="sticky" className="pdp-sticky-cta" {...motion}>
          {children}
        </m.div>
      )}
    </AnimatePresence>
  );
}
