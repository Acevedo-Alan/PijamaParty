import { describe, expect, it } from 'vitest';
import base from './base.css?raw';

describe('kaomoji (T-E7-52)', () => {
  it('given the base stylesheet, when the kaomoji rule is read, then its generated content has empty alternative text', () => {
    const rule = /\[data-kaomoji\]::after\s*\{([^}]*)\}/.exec(base)?.[1] ?? '';
    expect(rule).toMatch(/content:\s*' ' attr\(data-kaomoji\) \/ '';/);
  });
});
