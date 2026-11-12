import { describe, expect, it } from 'vitest';
import raw from './account.css?raw';
import area from './AccountArea.tsx?raw';

/** Every source file of the store outside the account, to check what they import. */
const others = import.meta.glob<string>(['../../**/*.{ts,tsx,css}', '!./**', '!../../**/*.test.{ts,tsx}'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

const css = raw.replace(/\r\n/g, '\n');

/** The declarations of every top-level rule written exactly with this selector. */
function rule(selector: string): string {
  const bodies = css
    .split(`\n${selector} {`)
    .slice(1)
    .map((rest) => rest.slice(0, rest.indexOf('}')));
  if (bodies.length === 0) {
    throw new Error(`Missing rule ${selector}`);
  }
  return bodies.join('\n');
}

describe('the account redesign (T-E7-63)', () => {
  it('given the account chunk, when it loads, then it brings Poppins and Open Sans and sets them on the area', () => {
    expect(area).toContain("import '@fontsource/poppins/600.css';");
    expect(area).toContain("import '@fontsource/open-sans/400.css';");
    expect(rule('.account-area')).toContain('font-family: var(--ff-account-text)');
    expect(css).toMatch(/\.account-area :is\(h1, h2, h3, h4\),[^{]*\{\s*font-family: var\(--ff-account-heading\)/);
  });

  it('given the rest of the store, when its sources are read, then none of them imports Poppins', () => {
    expect(Object.keys(others).length).toBeGreaterThan(20);
    for (const [path, source] of Object.entries(others)) {
      expect(source, path).not.toContain('@fontsource/poppins');
    }
  });

  it('given a phone, when the account opens, then every navigation pill carries the gold stitching inside its edge', () => {
    const stitches = rule('.account-nav-link::after');
    expect(stitches).toContain('inset: 3px');
    expect(stitches).toContain('border: 1px dashed var(--stitch-gold)');
    // From 860 px, only the sidebar's current link keeps it.
    expect(css).toContain(".account-nav-link:not([aria-current='page'])::after {\n    display: none;");
  });
});
