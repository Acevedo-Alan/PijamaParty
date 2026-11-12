import { describe, expect, it } from 'vitest';
import base from './base.css?raw';
import { contrast, resolve } from '../test/designTokens.ts';

/** The declarations of a rule of base.css, by its exact selector. */
function rule(selector: string): string {
  return new RegExp(`${selector.replace(/[.:()[\]]/g,'\\$&')} \\{([^}]*)\\}`).exec(base)?.[1] ?? '';
}

describe('the stitched button (T-E7-57, T-E7-59)', () => {
  it('given its rule, when read, then it has no fill, a fine border in its color and uppercase spaced text 44 px tall', () => {
    const button = rule('.btn-stitched');
    expect(button).toMatch(/background: none;/);
    expect(button).toMatch(/border: 1px solid var\(--stitched-color\);/);
    expect(button).toMatch(/text-transform: uppercase;/);
    expect(button).toMatch(/min-height: 44px;/);
    // Rose by default, for a light background.
    expect(button).toMatch(/--stitched-color: var\(--accent-text\);/);
    expect(button).toMatch(/--stitched-fill: var\(--accent\);/);
  });

  it('given its layers, when read, then the stitches are a dashed line inside it and the fill a layer that only fades (D-16)', () => {
    expect(rule('.btn-stitched::after')).toMatch(/border: 1px dashed var\(--stitched-line\);/);
    expect(rule('.btn-stitched::before')).toMatch(/opacity: 0;/);
    expect(rule('.btn-stitched::before')).toMatch(/transition: opacity /);
  });

  it('given hover, when read, then it fills only on hover devices and the arrow moves only without reduced motion', () => {
    expect(base).toMatch(/@media \(hover: hover\) \{\s*\.btn-stitched:hover \{\s*color: var\(--stitched-on-fill\);\s*\}\s*\.btn-stitched:hover::before \{\s*opacity: 1;/);
    expect(base).toMatch(/@media \(hover: hover\) and \(prefers-reduced-motion: no-preference\) \{\s*\.btn-stitched:hover \.btn-stitched-arrow \{\s*transform: translateX\(4px\);/);
  });

  it('given the rose colors, when measured, then the text keeps 4.5:1 on the page and, on hover, white on the fill', () => {
    expect(contrast(resolve('accent-text'), resolve('surface'))).toBeGreaterThanOrEqual(4.5);
    expect(contrast(resolve('accent-text'), resolve('bg'))).toBeGreaterThanOrEqual(4.5);
    expect(contrast(resolve('on-accent'), resolve('accent'))).toBeGreaterThanOrEqual(4.5);
  });
});

describe('the pending button (T-E7-69)', () => {
  it('given a request on its way, when read, then the button stays rose and a band of --accent sweeps it only by transform', () => {
    expect(rule('.btn[data-pending]')).toMatch(/background: var\(--accent-hover\);/);
    expect(rule('.btn[data-pending]::after')).toMatch(/linear-gradient\(90deg, transparent, var\(--accent\) 50%, transparent\)/);
    expect(base).toMatch(/@media \(prefers-reduced-motion: no-preference\) \{\s*\.btn\[data-pending\]::after \{\s*animation: btn-pending-sweep /);
    expect(base).toMatch(/@keyframes btn-pending-sweep \{\s*to \{\s*transform: translateX\(100%\);/);
  });

  it('given its two roses, when measured, then white text keeps 4.5:1 on both', () => {
    expect(contrast(resolve('on-accent'), resolve('accent-hover'))).toBeGreaterThanOrEqual(4.5);
    expect(contrast(resolve('on-accent'), resolve('accent'))).toBeGreaterThanOrEqual(4.5);
  });
});
