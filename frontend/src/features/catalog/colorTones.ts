/**
 * D-04: the tone of each color name the shop uses, for the swatches of the card and the product page. A color that
 * is not here keeps its name as text.
 */
export interface ColorTone {
  /** A CSS background: a #rrggbb color, or a gradient for a print. */
  background: string;
  /**
   * Light tones blend into the card background (WCAG 1.4.11 asks 3:1 for the swatch's edge), so they get the
   * strong border instead of the soft one.
   */
  outlined: boolean;
}

function tone(background: string, outlined = false): ColorTone {
  return { background, outlined };
}

/** By normalized name (see {@link normalizeColor}). */
const TONES: ReadonlyMap<string, ColorTone> = new Map([
  ['rosa', tone('#f2b8c6', true)],
  ['rosa viejo', tone('#d4a5a5', true)],
  ['fucsia', tone('#c2185b')],
  ['salvia', tone('#a9ae96', true)],
  ['verde', tone('#4f7a52')],
  ['verde agua', tone('#9fd3c7', true)],
  ['beige', tone('#e7dccb', true)],
  ['crudo', tone('#efe7d8', true)],
  ['arena', tone('#d9c4a5', true)],
  ['chocolate', tone('#6b5342')],
  ['marron', tone('#7b5a43')],
  ['terracota', tone('#c17a56')],
  ['naranja', tone('#e07b39', true)],
  ['mostaza', tone('#c9a227', true)],
  ['amarillo', tone('#f4d35e', true)],
  ['negro', tone('#222222')],
  ['blanco', tone('#ffffff', true)],
  ['gris', tone('#9e9e9e', true)],
  ['gris melange', tone('#b9b6b1', true)],
  ['celeste', tone('#a7d3ec', true)],
  ['azul', tone('#2f5d9e')],
  ['azul marino', tone('#1f2f4d')],
  ['rojo', tone('#c0392b')],
  ['bordo', tone('#7b1e2b')],
  ['lila', tone('#c8b6e2', true)],
  ['violeta', tone('#7d5ba6')],
  ['estampado', tone('linear-gradient(135deg, #f2b8c6 0 33%, #a7d3ec 33% 66%, #f4d35e 66%)', true)],
]);

/** Lowercase, without accents, with single spaces: "Bordó" and " bordo " are the same color. */
export function normalizeColor(name: string): string {
  return name
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ');
}

/** The tone of a color name; undefined when the map does not know it. */
export function colorTone(name: string): ColorTone | undefined {
  return TONES.get(normalizeColor(name));
}

/** Every tone of the map, for its tests. */
export function allColorTones(): ReadonlyMap<string, ColorTone> {
  return TONES;
}
