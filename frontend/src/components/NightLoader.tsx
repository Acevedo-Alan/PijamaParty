import './nightLoader.css';

/**
 * T-E7-54: one of the store's big waits, said by its loading text after a little crescent moon whose three "z" rise
 * while it waits. The moon and the "z" are decorative: the "z" are generated content with empty alternative text, so
 * the element's text, and what screen readers announce, is the loading text alone.
 */
export function NightLoader({ className, children }: { className?: string; children: string }) {
  return (
    <p className={className === undefined ? 'night-loader' : `night-loader ${className}`} role="status">
      <span className="night-loader-moon" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="M15.5 3.5a8.5 8.5 0 1 0 5 15.4A7 7 0 0 1 15.5 3.5Z" />
        </svg>
        <span className="night-loader-z" />
        <span className="night-loader-z" />
        <span className="night-loader-z" />
      </span>
      {children}
    </p>
  );
}
