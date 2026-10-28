import { m } from 'motion/react';
import { ButtonProgress, type ProgressState } from '../../components/ButtonProgress.tsx';
import { useMotionReady } from '../../components/motion/MotionProvider.tsx';
import { useActionMotion } from '../../components/motion/useActionMotion.ts';
import { usePrefersReducedMotion } from '../../components/motion/usePrefersReducedMotion.ts';
import { es } from '../../i18n/es.ts';
import { duration, ease } from '../../styles/motion.ts';
import type { AddAnswer } from '../cart/cartApi.ts';

/** Where the add button's progress bar is: adding, added for a moment, or rejected or failed (E7 motion). */
export function addButtonState(add: {
  isPending: boolean;
  isError: boolean;
  data: AddAnswer | undefined;
}, confirmed: boolean): ProgressState {
  if (add.isPending) {
    return 'loading';
  }
  if (confirmed) {
    return 'done';
  }
  if (add.isError || (add.data !== undefined && add.data.status !== 'added')) {
    return 'failed';
  }
  return 'idle';
}

/** Where each sparkle flies from the middle of the button, in px. */
const SPARKS = [
  { x: -120, y: -30 },
  { x: -64, y: -38 },
  { x: 0, y: -40 },
  { x: 64, y: -36 },
  { x: 120, y: -26 },
  { x: -96, y: 30 },
  { x: 96, y: 32 },
] as const;

/** The sparkles that burst out of the button once when an add completes; none under reduced motion. */
function Sparkles() {
  const ready = useMotionReady();
  const reduced = usePrefersReducedMotion();
  if (!ready || reduced) {
    return null;
  }
  return (
    <span className="add-cart-sparkles" aria-hidden="true">
      {SPARKS.map(({ x, y }, step) => (
        <m.span
          key={`${String(x)},${String(y)}`}
          className="add-cart-spark"
          initial={{ x: 0, y: 0, scale: 0, opacity: 1 }}
          animate={{ x, y, scale: [0, 1.2, 0.6], opacity: [1, 1, 0] }}
          transition={{ duration: duration.slow, ease: ease.out, delay: step * 0.02 }}
        >
          ✦
        </m.span>
      ))}
    </span>
  );
}

/** The inside of the add button: the rainbow bar under what it does, that it is adding, or that it added. */
export function AddButtonLabel({ state }: { state: ProgressState }) {
  const check = useActionMotion({
    hidden: { scale: 0 },
    shown: { scale: 1 },
    transition: { duration: duration.base, ease: ease.pop },
  });
  const bar = <ButtonProgress />;
  if (state === 'loading') {
    return (
      <>
        {bar}
        {es.product.adding}
      </>
    );
  }
  if (state !== 'done') {
    return (
      <>
        {bar}
        {es.product.addToCart}
      </>
    );
  }
  return (
    <>
      {bar}
      <m.svg className="add-cart-check" viewBox="0 0 24 24" aria-hidden="true" {...check}>
        <path d="M5 12.5l4.5 4.5L19 7.5" />
      </m.svg>
      {es.product.added}
      <Sparkles />
    </>
  );
}
