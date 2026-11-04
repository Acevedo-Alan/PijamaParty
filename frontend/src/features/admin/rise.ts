import type { CSSProperties } from 'react';

/**
 * D-E8-12 motion: the cards of a screen rise in one after another when it first shows, once, 50 ms apart; a refresh
 * keeps them mounted, so it does not move the screen. Nothing moves under reduced motion (motion-safe).
 */
export function rise(order: number): { className: string; style: CSSProperties } {
  return { className: 'motion-safe:animate-rise', style: { animationDelay: `${String(order * 50)}ms` } };
}
