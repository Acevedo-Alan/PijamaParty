import { m } from 'motion/react';
import { useEffect, useState } from 'react';
import { usePrefersReducedMotion } from '../../components/motion/usePrefersReducedMotion.ts';
import { ease } from '../../styles/motion.ts';

/** The order numbers this browser already celebrated (T-E7-78): a convenience, never a source of truth. */
const KEY = 'pp-celebrated-orders';
/** Enough for years of one buyer's orders, without the list growing forever. */
const KEPT = 50;

function celebrated(): number[] {
  try {
    const stored: unknown = JSON.parse(window.localStorage.getItem(KEY) ?? '[]');
    return Array.isArray(stored) ? stored.filter((value): value is number => typeof value === 'number') : [];
  } catch {
    return [];
  }
}

function remember(number: number): void {
  try {
    window.localStorage.setItem(KEY, JSON.stringify([...celebrated().filter((n) => n !== number), number].slice(-KEPT)));
  } catch {
    // Without storage the page celebrates once per visit.
  }
}

const COLORS = ['var(--spark-pink)', 'var(--spark-gold)', 'var(--spark-blue)', 'var(--accent)'] as const;
const COUNT = 14;
const DURATION_S = 1.4;
const STEP_S = 0.03;

/** A four-pointed star and a crescent moon, drawn in a 24 px box. */
const STAR = 'M12 2.5c.6 4.6 2.9 6.9 7.5 7.5-4.6.6-6.9 2.9-7.5 7.5-.6-4.6-2.9-6.9-7.5-7.5 4.6-.6 6.9-2.9 7.5-7.5Z';
const MOON = 'M15.5 3.5a8.5 8.5 0 1 0 5 13.6A7 7 0 0 1 15.5 3.5Z';

/**
 * Where each piece flies: evenly around the chibi, skipping straight down (the heading is there), 80 to 150 px out,
 * fixed rather than random so the burst looks the same every time and the tests can read it.
 */
const PIECES = Array.from({ length: COUNT }, (_, index) => {
  const angle = (-90 + (index * 300) / (COUNT - 1) - 150) * (Math.PI / 180);
  const distance = 80 + ((index * 37) % 71);
  return {
    shape: index % 3 === 1 ? MOON : STAR,
    color: COLORS[index % COLORS.length] ?? COLORS[0],
    size: 12 + ((index * 5) % 10),
    x: Math.round(Math.cos(angle) * distance),
    y: Math.round(Math.sin(angle) * distance),
    turn: index % 2 === 0 ? 40 : -40,
  };
});

/**
 * T-E7-78: the purchase celebration. The first time this browser shows an order in `PAID`, stars and moons burst
 * from behind the chibi and fade, once per order; not under reduced motion. Decorative: the heading says it.
 */
export function PaidCelebration({ number }: { number: number }) {
  const reduced = usePrefersReducedMotion();
  // Decided once when the header mounts, so remembering the order below does not take the burst away.
  const [burst] = useState(() => !celebrated().includes(number));

  useEffect(() => {
    if (burst) {
      remember(number);
    }
  }, [burst, number]);

  if (!burst || reduced) {
    return null;
  }
  return (
    <span className="paid-celebration" aria-hidden="true">
      {PIECES.map((piece, index) => (
        <m.svg
          key={index}
          className="paid-celebration-piece"
          viewBox="0 0 24 24"
          width={piece.size}
          height={piece.size}
          style={{ color: piece.color }}
          initial={{ x: 0, y: 0, scale: 0, opacity: 0, rotate: 0 }}
          animate={{
            x: piece.x,
            y: piece.y,
            scale: [0, 1.15, 0.8],
            opacity: [0, 1, 0],
            rotate: piece.turn,
          }}
          transition={{ duration: DURATION_S, delay: index * STEP_S, ease: ease.out }}
        >
          <path d={piece.shape} />
        </m.svg>
      ))}
    </span>
  );
}
