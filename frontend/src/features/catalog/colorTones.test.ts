import { describe, expect, it } from 'vitest';
import { contrast, resolve } from '../../test/designTokens.ts';
import { allColorTones, colorTone, normalizeColor } from './colorTones.ts';

/** The backgrounds a swatch sits on: the page behind the grid and the white of a list row or the product page. */
const CARD_BACKGROUNDS = ['bg', 'surface'];

describe('color tones (D-04)', () => {
  it('given names with capitals, accents or extra spaces, when normalized, then they are the same color', () => {
    expect(normalizeColor(' Bordó ')).toBe('bordo');
    expect(normalizeColor('ROSA  Viejo')).toBe('rosa viejo');
    expect(colorTone('Marrón')).toEqual(colorTone('marron'));
    expect(colorTone('Rosa')).toBeDefined();
  });

  it('given a color the map does not know, when looked up, then it has no tone and keeps its name', () => {
    expect(colorTone('Unicornio')).toBeUndefined();
  });

  it('given every tone, when drawn on a card background, then its edge reaches the 3:1 of WCAG 1.4.11', () => {
    for (const background of CARD_BACKGROUNDS) {
      expect(contrast(resolve('line-strong'), resolve(background))).toBeGreaterThanOrEqual(3);
      for (const [name, { background: fill, outlined }] of allColorTones()) {
        if (!outlined) {
          expect(fill, name).toMatch(/^#[0-9a-f]{6}$/);
          expect(contrast(fill, resolve(background)), `${name} on --${background}`).toBeGreaterThanOrEqual(3);
        }
      }
    }
  });
});
