import { describe, expect, it } from 'vitest';
import raw from './catalog.css?raw';

const css = raw.replace(/\r\n/g, '\n');

/** The declarations of the top-level rule written exactly with this selector. */
function rule(selector: string): string {
  const start = css.indexOf(`\n${selector} {`);
  if (start < 0) {
    throw new Error(`Missing rule ${selector}`);
  }
  return css.slice(start, css.indexOf('}', start));
}

describe('the grid card (T-E7-71)', () => {
  it('given the swatches and the sizes, when they do not fit, then the row stays one line and only the sizes are cut', () => {
    expect(rule('.card-meta')).toContain('flex-wrap: nowrap');
    expect(rule('.card-meta .swatches-mini')).toContain('flex: none');
    expect(rule('.card-sizes')).toMatch(/min-width: 0;[\s\S]*overflow: hidden;[\s\S]*text-overflow: ellipsis;/);
  });

  it('given cards of a row with names of one and two lines, when shown, then each price sits at its card\'s bottom', () => {
    expect(rule('.card')).toContain('flex-direction: column');
    expect(rule('.card .price')).toContain('margin-top: auto');
  });
});
