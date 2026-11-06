import { describe, expect, it } from 'vitest';

const stylesheets = import.meta.glob<string>('../**/*.css', { query: '?raw', import: 'default', eager: true });

/** D-16 (T-E8-25): only transform and opacity move. `visibility` rides along to hide a panel after it fades. */
const ALLOWED = new Set(['transform', 'opacity', 'scale', 'visibility', 'none']);
/** The one exception: the collapsible section grows its row, which no transform can do. */
const EXCEPTIONS = new Set(['../components/Collapsible.css']);

describe('stylesheets (D-16, T-E8-25)', () => {
  it('given every stylesheet, when its transitions are read, then each one moves only transform or opacity', () => {
    const offenders: string[] = [];
    for (const [file, css] of Object.entries(stylesheets)) {
      if (EXCEPTIONS.has(file)) {
        continue;
      }
      for (const [, value] of css.matchAll(/(?<![\w-])transition:\s*([^;]+);/g)) {
        for (const part of (value ?? '').split(/,(?![^()]*\))/)) {
          const property = part.trim().split(/\s+/)[0] ?? '';
          if (!ALLOWED.has(property)) {
            offenders.push(`${file}: ${property}`);
          }
        }
      }
    }
    expect(offenders).toEqual([]);
  });

  it('given every stylesheet, when read, then no replacement placeholder was left in a declaration (T-E8-25)', () => {
    const broken = Object.entries(stylesheets)
      .filter(([, css]) => /\$\d/.test(css))
      .map(([file]) => file);
    expect(broken).toEqual([]);
  });
});
