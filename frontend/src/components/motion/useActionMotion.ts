import type { MotionProps, TargetAndTransition, Transition } from 'motion/react';
import { duration, ease } from '../../styles/motion.ts';
import { useMotionReady } from './MotionProvider.tsx';
import { usePrefersReducedMotion } from './usePrefersReducedMotion.ts';

interface ActionMotion {
  /** Where the element comes from and goes back to: transform and opacity only. */
  hidden: TargetAndTransition;
  shown: TargetAndTransition;
  transition: Transition;
}

const FADE: Pick<MotionProps, 'animate' | 'exit' | 'transition'> = {
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: duration.fast, ease: ease.standard },
};

/**
 * The props of an `m` element that appears in answer to an action (E7 motion): it enters from `hidden` and leaves
 * back to it. Under reduced motion it only fades, shortly. Until Motion's features load it shows in place.
 */
export function useActionMotion({ hidden, shown, transition }: ActionMotion) {
  const ready = useMotionReady();
  const reduced = usePrefersReducedMotion();
  if (reduced) {
    return { ...FADE, initial: ready ? { opacity: 0 } : false } satisfies MotionProps;
  }
  return { initial: ready ? hidden : false, animate: shown, exit: hidden, transition } satisfies MotionProps;
}
