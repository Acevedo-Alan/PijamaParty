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

  it('given the account, when its navigation shows, then the pills have a thin solid edge and no stitching (T-E7-68)', () => {
    expect(rule('.account-nav-link')).toContain('border: 1px solid var(--line)');
    expect(css).not.toContain('.account-nav-link::after');
  });

  it('given a signed-in header, when it shows, then the initials are a rose circle in the icon\'s place, without the dot (T-E7-72)', () => {
    expect(rule('.account-icon-initials')).toContain('background: var(--rose-soft)');
    expect(rule('.account-icon-initials')).toContain('color: var(--accent-text)');
    expect(rule('.account-icon[data-initials]::after')).toContain('display: none');
    expect(rule('.account-icon[data-leaving] .account-icon-initials')).toContain('opacity: 0');
  });

  it('given an empty address book, when it shows, then it is the one dashed card of the account (T-E7-68)', () => {
    expect(rule('.address-empty')).toContain('border: 1.5px dashed var(--line-strong)');
    expect(rule('.address-empty')).toContain('background: var(--surface)');
  });
});
