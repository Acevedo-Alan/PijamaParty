import { describe, expect, it } from 'vitest';
import css from './catalog.css?raw';
import { contrast, resolve } from '../../test/designTokens.ts';

/** The capsule's tint of `--surface` over the photo, as written in catalog.css. */
function capsuleOpacity(): number {
  const rule = /\.badge \{[^}]*background: color-mix\(in srgb, var\(--surface\) (\d+)%, transparent\)/.exec(css);
  if (rule?.[1] === undefined) {
    throw new Error('Missing the .badge background');
  }
  return Number(rule[1]) / 100;
}

/** The capsule blended over a black photo, the darkest one a card can show. */
function capsuleOverBlack(): string {
  const surface = resolve('surface');
  const alpha = capsuleOpacity();
  return (
    '#' +
    [1, 3, 5]
      .map((start) => Math.round(parseInt(surface.slice(start, start + 2), 16) * alpha))
      .map((channel) => channel.toString(16).padStart(2, '0'))
      .join('')
  );
}

describe('card labels (UC-02, D-05)', () => {
  it.each(['accent-text', 'ink-soft'])(
    'given the darkest photo, when a label in --%s shows on its capsule, then the text keeps 4.5:1',
    (text) => {
      expect(contrast(resolve(text), capsuleOverBlack())).toBeGreaterThanOrEqual(4.5);
    },
  );
});
