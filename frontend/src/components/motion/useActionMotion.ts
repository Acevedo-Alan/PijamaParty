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

/** The steps of a staggered entrance (T-E7-17): 40 ms apart, at most 6, so the last item never waits long. */
const STAGGER_STEP_S = 0.04;
const STAGGER_MAX_STEPS = 6;

/** One item of a staggered entrance: the same motion, starting `step` steps later. */
export function staggered<T extends { transition?: Transition | undefined }>(motion: T, step: number): T {
  const delay = Math.min(step, STAGGER_MAX_STEPS) * STAGGER_STEP_S;
  return { ...motion, transition: { ...motion.transition, delay } };
}

/** The entrance of what joins the page (T-E7-17): it rises 12 px, or comes 12 px from the left, and fades in. */
export function useEntranceMotion(from: 'below' | 'left' = 'below') {
  return useActionMotion({
    hidden: from === 'below' ? { y: 12, opacity: 0 } : { x: -12, opacity: 0 },
    shown: from === 'below' ? { y: 0, opacity: 1 } : { x: 0, opacity: 1 },
    transition: { duration: duration.base, ease: ease.out },
  });
}
