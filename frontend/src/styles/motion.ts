/**
 * The motion tokens of tokens.css for the Motion library (E7, D-16): durations in seconds and easings as the points of
 * their cubic-bezier. Change a value in both places; tokens.test.ts checks that they agree.
 */
export const duration = {
  fast: 0.12,
  base: 0.24,
  panel: 0.32,
  slow: 0.45,
  entrance: 0.7,
} as const;

export const ease = {
  out: [0.2, 0.7, 0.2, 1],
  standard: [0.4, 0, 0.2, 1],
  pop: [0.34, 1.56, 0.64, 1],
} as const satisfies Record<string, readonly [number, number, number, number]>;
