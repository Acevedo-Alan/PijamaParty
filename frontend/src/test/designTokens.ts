import css from '../styles/tokens.css?raw';

/** The value of a token of tokens.css as written. */
export function token(name: string): string {
  const match = new RegExp(`--${name}:\\s*([^;]+);`).exec(css);
  if (match?.[1] === undefined) {
    throw new Error(`Missing token --${name}`);
  }
  return match[1].trim();
}

/** The value of a token, following `var(--other)` references. */
export function resolve(name: string): string {
  const value = token(name);
  const reference = /^var\(--([\w-]+)\)$/.exec(value);
  return reference?.[1] === undefined ? value : resolve(reference[1]);
}

/** WCAG 2.1 contrast ratio between two #rrggbb colors. */
export function contrast(foreground: string, background: string): number {
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
