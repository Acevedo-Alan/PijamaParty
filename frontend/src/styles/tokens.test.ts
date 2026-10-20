import { describe, expect, it } from 'vitest';
import { contrast, resolve, token } from '../test/designTokens.ts';
import { duration, ease } from './motion.ts';

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

describe('motion tokens (D-16)', () => {
  it('given the E7 motion section, when reading the CSS tokens, then they keep its durations and easings', () => {
    expect({
      fast: token('dur-fast'), base: token('dur-base'), panel: token('dur-panel'), slow: token('dur-slow'),
      entrance: token('dur-entrance'),
      out: token('ease-out'), standard: token('ease-standard'), pop: token('ease-pop'),
    }).toEqual({
      fast: '120ms', base: '240ms', panel: '320ms', slow: '450ms', entrance: '700ms',
      out: 'cubic-bezier(0.2, 0.7, 0.2, 1)', standard: 'cubic-bezier(0.4, 0, 0.2, 1)',
      pop: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
    });
  });

  it('given motion.ts, when compared with the CSS tokens, then every duration and easing agrees', () => {
    for (const [name, seconds] of Object.entries(duration)) {
      expect(token(`dur-${name}`), name).toBe(`${String(Math.round(seconds * 1000))}ms`);
    }
    for (const [name, points] of Object.entries(ease)) {
      expect(token(`ease-${name}`), name).toBe(`cubic-bezier(${points.join(', ')})`);
    }
  });

  it('given the motion rules, when reading the durations, then they stay between 120 and 450 ms but the entrance', () => {
    const { entrance, ...rest } = duration;
    for (const seconds of Object.values(rest)) {
      expect(seconds).toBeGreaterThanOrEqual(0.12);
      expect(seconds).toBeLessThanOrEqual(0.45);
    }
    expect(entrance).toBeLessThanOrEqual(0.7);
  });
});
