import { useState } from 'react';
import { usePrefersReducedMotion } from '../../components/motion/usePrefersReducedMotion.ts';
import { isSakuraSeason } from './sakuraSeason.ts';
import './sakura.css';

const PETALS = [0, 1, 2, 3, 4, 5, 6, 7];

/**
 * T-E7-56: eight cherry-blossom petals falling across the home carousel in spring, over its slides and veil and under
 * its text and buttons. Decorative: hidden from screen readers and never catching a tap. Out of season, or under
 * reduced motion, nothing is rendered.
 */
export function SakuraPetals() {
  const [inSeason] = useState(() => isSakuraSeason(new Date()));
  const reduced = usePrefersReducedMotion();
  if (!inSeason || reduced) {
    return null;
  }
  return (
    <div className="sakura" aria-hidden="true">
      {PETALS.map((petal) => (
        <span key={petal} className="sakura-petal">
          <svg viewBox="-11 -11 22 22">
            <path d="M0 -6 L-2.5 -10 C-8 -10 -10 -2 -6 3 C-4 6 -1 9 0 10 C1 9 4 6 6 3 C10 -2 8 -10 2.5 -10 Z" />
          </svg>
        </span>
      ))}
    </div>
  );
}
