import { es } from '../../i18n/es.ts';

interface ShowMoreProps {
  /** Products on the screen and how many match in all (D-11). */
  seen: number;
  total: number;
  loading: boolean;
  failed: boolean;
  onMore: () => void;
}

/**
 * "Ver más" (D-10) as a tinted capsule (Apple HIG: a secondary action is tinted, not filled): how far the visitor got,
 * "Viste 24 de 134" over a thin bar, then the button with a chevron down that turns into a spinner while the next
 * page loads (E7, changes to UC-01).
 */
export function ShowMore({ seen, total, loading, failed, onMore }: ShowMoreProps) {
  // A page can arrive before the count catches up; the bar never runs past full.
  const share = total > 0 ? Math.min(1, seen / total) : 0;
  return (
    <div className="show-more">
      {failed && (
        <p className="show-more-error" role="alert">
          {es.catalog.loadMoreError}
        </p>
      )}
      {/* Read after each page arrives, so a screen reader hears that more products came (the count above stays). */}
      <p className="show-more-progress" role="status">
        {es.catalog.seenOf(Math.min(seen, total), total)}
        <span className="show-more-bar" aria-hidden="true">
          <span style={{ transform: `scaleX(${String(share)})` }} />
        </span>
      </p>
      <button type="button" className="load-more-btn" disabled={loading} aria-busy={loading} onClick={onMore}>
        {loading ? es.catalog.loadingMore : es.catalog.showMore}
        <span className={loading ? 'show-more-icon spinning' : 'show-more-icon'} aria-hidden="true">
          {loading ? (
            <svg viewBox="0 0 20 20">
              <circle cx="10" cy="10" r="7" />
            </svg>
          ) : (
            <svg viewBox="0 0 20 20">
              <path d="M5.5 8l4.5 4.5L14.5 8" />
            </svg>
          )}
        </span>
      </button>
    </div>
  );
}
