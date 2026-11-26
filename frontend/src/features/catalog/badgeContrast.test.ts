import { describe, expect, it } from "vitest";
import css from "./catalog.css?raw";
import { contrast, resolve } from "../../test/designTokens.ts";

/** The tab's background, as written in catalog.css (T-E7-79): a token, so nothing of the photo shows through. */
function tabBackground(): string {
  const rule = /\.badge \{[^}]*background: var\(--([a-z-]+)\);/.exec(
    css.replace(/\r\n/g, "\n"),
  );
  if (rule?.[1] === undefined) {
    throw new Error("Missing the .badge background");
  }
  return rule[1];
}

describe("card labels (UC-02, D-05, T-E7-79)", () => {
  it("given the sewn-on tab, when it shows over a photo, then its background is the opaque page cream", () => {
    expect(tabBackground()).toBe("bg");
    expect(resolve(tabBackground())).toMatch(/^#[0-9a-f]{6}$/i);
  });

  it.each(["accent-text", "ink-soft"])(
    "given any photo, when a label in --%s shows on its tab, then the text keeps 4.5:1",
    (text) => {
      expect(
        contrast(resolve(text), resolve(tabBackground())),
      ).toBeGreaterThanOrEqual(4.5);
    },
  );
});
