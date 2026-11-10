/** The nine stars of the strip, in order along it: a four-pointed spark or a dot (their places are in footer.css). */
const STARS = ['spark', 'dot', 'spark', 'dot', 'spark', 'dot', 'spark', 'dot', 'spark'] as const;

/**
 * T-E7-54: the night strip across the top of the footer, a crescent moon at the right end of the page width and nine
 * little stars that twinkle, each at its own pace. Decorative only: hidden from screen readers, with no text on it.
 */
export function FooterNight() {
  return (
    <div className="footer-night" aria-hidden="true">
      <div className="footer-night-inner">
        {STARS.map((kind, index) => (
          <span key={index} className={`footer-star footer-star-${kind}`} />
        ))}
        <svg className="footer-moon" viewBox="0 0 24 24">
          <path d="M15.5 3.5a8.5 8.5 0 1 0 5 15.4A7 7 0 0 1 15.5 3.5Z" />
        </svg>
      </div>
    </div>
  );
}
