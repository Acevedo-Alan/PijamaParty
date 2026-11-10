/** The nine stars of the strip, in order along it: a four-pointed spark or a dot (their places are in footer.css). */
const STARS = ['spark', 'dot', 'spark', 'dot', 'spark', 'dot', 'spark', 'dot', 'spark'] as const;

/** A lucky cloud (祥云, T-E7-55): a soft body with two curls, the curling clouds of Chinese art. */
function LuckyCloud({ className }: { className: string }) {
  return (
    <svg className={`footer-cloud ${className}`} viewBox="40 36 114 40">
      <path
        className="footer-cloud-body"
        d="M48 70 C42 70 42 60 50 59 C50 50 62 46 70 52 C74 40 92 38 98 50 C104 42 120 44 122 56 C132 52 144 58 142 66 C150 66 152 74 144 74 L52 74 C49 74 48 72 48 70 Z"
      />
      <path className="footer-cloud-curl" d="M70 62 c0 -6 8 -6 8 0 c0 3 -4 3 -4 1 M102 62 c0 -7 10 -7 10 0 c0 4 -5 4 -5 1" />
    </svg>
  );
}

/**
 * The store's moon rabbit sitting in the crescent's hollow (T-E7-55): an original drawing in the chibi's style, round
 * as a mochi, one ear up and one floppy; its eyes blink now and then (footer.css).
 */
function MoonWithRabbit() {
  return (
    <svg className="footer-moon" viewBox="0 0 100 80">
      <path className="footer-moon-crescent" d="M44 6 a36 36 0 1 0 34 54 a28 28 0 0 1 -34 -54 Z" />
      <g transform="translate(46 20) scale(0.4)">
        <g className="footer-rabbit-line">
          <ellipse cx="44" cy="30" rx="10" ry="25" transform="rotate(-10 44 30)" className="footer-rabbit-fur" />
          <ellipse cx="44" cy="33" rx="4.5" ry="16" transform="rotate(-10 44 33)" className="footer-rabbit-ear" />
          <ellipse cx="80" cy="36" rx="10" ry="23" transform="rotate(32 80 36)" className="footer-rabbit-fur" />
          <ellipse cx="80" cy="38" rx="4.5" ry="14" transform="rotate(32 80 38)" className="footer-rabbit-ear" />
          <ellipse cx="60" cy="98" rx="30" ry="21" className="footer-rabbit-fur" />
          <ellipse cx="60" cy="68" rx="35" ry="28" className="footer-rabbit-fur" />
        </g>
        <ellipse cx="37" cy="77" rx="5.5" ry="3.2" className="footer-rabbit-blush" />
        <ellipse cx="83" cy="77" rx="5.5" ry="3.2" className="footer-rabbit-blush" />
        <g className="footer-rabbit-eyes">
          <ellipse cx="47" cy="68" rx="4.6" ry="6.4" className="footer-rabbit-pupil" />
          <ellipse cx="73" cy="68" rx="4.6" ry="6.4" className="footer-rabbit-pupil" />
          <circle cx="48.8" cy="65.4" r="2" className="footer-rabbit-shine" />
          <circle cx="74.8" cy="65.4" r="2" className="footer-rabbit-shine" />
        </g>
        <path className="footer-rabbit-mouth" d="M55.5 76.5 q2.25 2.8 4.5 0 q2.25 2.8 4.5 0" />
      </g>
    </svg>
  );
}

/**
 * T-E7-54, T-E7-55: the night strip across the top of the footer: two lucky clouds that drift, nine little stars that
 * twinkle, each at its own pace, and the crescent moon at the right end of the page width with the moon rabbit in it.
 * Decorative only: hidden from screen readers, with no text on it.
 */
export function FooterNight() {
  return (
    <div className="footer-night" aria-hidden="true">
      <div className="footer-night-inner">
        {STARS.map((kind, index) => (
          <span key={index} className={`footer-star footer-star-${kind}`} />
        ))}
        <LuckyCloud className="footer-cloud-left" />
        <LuckyCloud className="footer-cloud-middle" />
        <MoonWithRabbit />
      </div>
    </div>
  );
}
