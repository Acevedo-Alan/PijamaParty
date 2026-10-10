import { describe, expect, it } from 'vitest';
import css from './tokens.css?raw';

function token(name: string): string {
  const match = new RegExp(`--${name}:\\s*([^;]+);`).exec(css);
  if (match?.[1] === undefined) {
    throw new Error(`Missing token --${name}`);
  }
  return match[1].trim();
}

function resolve(name: string): string {
  const value = token(name);
  const reference = /^var\(--([\w-]+)\)$/.exec(value);
  return reference?.[1] === undefined ? value : resolve(reference[1]);
}

/** WCAG 2.1 contrast ratio between two #rrggbb colors. */
function contrast(foreground: string, background: string): number {
  const luminance = (hex: string): number => {
    const [r, g, b] = [1, 3, 5].map((start) => {
      const channel = parseInt(hex.slice(start, start + 2), 16) / 255;
      return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0);
  };
  const [lighter, darker] = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
  return ((lighter ?? 0) + 0.05) / ((darker ?? 0) + 0.05);
}

describe('design tokens', () => {
  it('given the prototype, when reading the tokens, then they keep its names and values', () => {
    expect({
      bg: token('bg'), surface: token('surface'), ink: token('ink'), 'ink-soft': token('ink-soft'),
      peach: token('peach'), 'peach-soft': token('peach-soft'), rose: token('rose'), 'rose-soft': token('rose-soft'),
      accent: token('accent'), 'accent-hover': token('accent-hover'), 'accent-soft': token('accent-soft'),
      line: token('line'), danger: token('danger'),
      'radius-s': token('radius-s'), 'radius-m': token('radius-m'), maxw: token('maxw'),
    }).toEqual({
      bg: '#fff8f0', surface: '#ffffff', ink: '#4a3a3a', 'ink-soft': '#7a6a66',
      peach: '#ffd9c7', 'peach-soft': '#ffede3', rose: '#f7c6cf', 'rose-soft': '#fce4e8',
      accent: '#c2506a', 'accent-hover': '#a83f57', 'accent-soft': '#fce4e8',
      line: '#f0dcd0', danger: '#b84332',
      'radius-s': '12px', 'radius-m': '16px', maxw: '1180px',
    });
    expect(token('ff-body')).toContain('Quicksand');
    expect(token('ff-display')).toContain('Quicksand');
  });

  it('given small text and links, when drawn on the store backgrounds, then they reach WCAG AA 4.5:1', () => {
    for (const background of ['bg', 'surface', 'rose-soft', 'peach-soft']) {
      expect(contrast(resolve('accent-text'), resolve(background))).toBeGreaterThanOrEqual(4.5);
      expect(contrast(resolve('ink'), resolve(background))).toBeGreaterThanOrEqual(4.5);
    }
    expect(contrast(resolve('ink-soft'), resolve('bg'))).toBeGreaterThanOrEqual(4.5);
    expect(contrast('#ffffff', resolve('accent'))).toBeGreaterThanOrEqual(4.5);
  });

  it('given form controls, when their border is drawn, then it reaches the 3:1 WCAG asks for UI components', () => {
    for (const background of ['bg', 'surface', 'peach-soft']) {
      expect(contrast(resolve('line-strong'), resolve(background))).toBeGreaterThanOrEqual(3);
    }
  });

  it('given the prototype accent, when used as small text on the cream background, then it would fail AA', () => {
    // The reason --accent-text exists: keep it documented by a test.
    expect(contrast(resolve('accent'), resolve('bg'))).toBeLessThan(4.5);
  });
});
