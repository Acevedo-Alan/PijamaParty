import { es } from '../../i18n/es.ts';
import type { ListingView } from './useListingView.ts';

interface ViewToggleProps {
  value: ListingView;
  onChange: (view: ListingView) => void;
}

/** The two buttons that show the listing as a grid or a list; the pressed one is the current view. */
export function ViewToggle({ value, onChange }: ViewToggleProps) {
  return (
    <div className="view-toggle" role="group" aria-label={es.catalog.view}>
      <button
        type="button"
        aria-label={es.catalog.gridView}
        aria-pressed={value === 'grid'}
        onClick={() => {
          onChange('grid');
        }}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <rect x="3" y="3" width="7" height="7" rx="1.5" />
          <rect x="14" y="3" width="7" height="7" rx="1.5" />
          <rect x="3" y="14" width="7" height="7" rx="1.5" />
          <rect x="14" y="14" width="7" height="7" rx="1.5" />
        </svg>
      </button>
      <button
        type="button"
        aria-label={es.catalog.listView}
        aria-pressed={value === 'list'}
        onClick={() => {
          onChange('list');
        }}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M8 6h11M8 12h11M8 18h11" />
          <circle cx="4" cy="6" r="1" />
          <circle cx="4" cy="12" r="1" />
          <circle cx="4" cy="18" r="1" />
        </svg>
      </button>
    </div>
  );
}
