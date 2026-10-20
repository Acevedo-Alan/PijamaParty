import { m } from 'motion/react';
import { useState } from 'react';
import { useActionMotion } from '../../components/motion/useActionMotion.ts';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { duration, ease } from '../../styles/motion.ts';
import { useShippingRates } from '../help/useShippingRates.ts';

/**
 * UC-08 (E7): how much is missing for free shipping, or that the cart has it, with a bar that fills up to the
 * threshold. It compares the server's total after discount with the threshold; the checkout still decides the cost
 * (UC-12). Without a threshold, or while it is unknown, nothing.
 */
export function FreeShippingProgress({ totalCents }: { totalCents: number }) {
  const threshold = useShippingRates().data?.freeShippingThresholdCents ?? null;
  const missing = threshold === null ? 0 : threshold - totalCents;
  const reached = missing <= 0;
  // The message pops only when the cart reaches the threshold here, not when the panel opens with it reached.
  const [wasReached, setWasReached] = useState(reached);
  const [pops, setPops] = useState(false);
  if (reached !== wasReached) {
    setWasReached(reached);
    setPops(reached);
  }
  const motion = useActionMotion({
    hidden: { scale: 0.9, opacity: 0 },
    shown: { scale: 1, opacity: 1 },
    transition: { duration: duration.slow, ease: ease.pop },
  });

  if (threshold === null) {
    return null;
  }
  const filled = reached ? 1 : Math.max(0, totalCents) / threshold;
  return (
    <div className="ship-progress">
      <m.p key={String(reached)} {...motion} initial={pops ? motion.initial : false}>
        {reached ? es.cart.freeShippingReached : es.cart.freeShippingMissing(formatMoney(missing))}
      </m.p>
      <div className="ship-bar" aria-hidden="true">
        <span style={{ transform: `scaleX(${String(filled)})` }} />
      </div>
    </div>
  );
}
