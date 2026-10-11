import { useRef, useState, type MouseEvent } from 'react';
import { useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { searchText } from '../catalog/listingCriteria.ts';
import { SearchBox } from './SearchBox.tsx';

/** The text of the results page shown, so the field repeats it; empty on any other page. */
function useShownText(): string {
  const { pathname, search } = useLocation();
  return pathname === '/buscar' ? (searchText(new URLSearchParams(search).get('q')) ?? '') : '';
}

/**
 * The header search (UC-26): a field from 960 px, and below that an icon that opens a full-screen search. Both are
 * rendered and CSS shows one; the full-screen one only mounts its field while open, so it starts focused.
 */
export function HeaderSearch() {
  const shownText = useShownText();
  const dialog = useRef<HTMLDialogElement>(null);
  const [screenOpen, setScreenOpen] = useState(false);

  function open() {
    setScreenOpen(true);
    dialog.current?.showModal();
  }

  function close() {
    dialog.current?.close();
  }

  function closeOnBackdrop(event: MouseEvent<HTMLDialogElement>) {
    if (event.target === event.currentTarget) {
      close();
    }
  }

  return (
    <div className="header-search">
      <div className="header-search-inline">
        <SearchBox key={shownText} initialText={shownText} />
      </div>
      <button
        type="button"
        className="icon-btn search-trigger"
        aria-label={es.search.open}
        aria-haspopup="dialog"
        onClick={open}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="11" cy="11" r="5.5" />
          <path d="M16 16l4 4" strokeLinecap="round" />
        </svg>
      </button>
      <dialog
        ref={dialog}
        className="search-screen"
        aria-label={es.search.title}
        onClose={() => {
          setScreenOpen(false);
        }}
        onClick={closeOnBackdrop}
      >
        {screenOpen && (
          <div className="search-screen-bar">
            <SearchBox initialText={shownText} autoFocus onNavigate={close} />
            <button type="button" className="search-screen-close" onClick={close}>
              {es.search.close}
            </button>
          </div>
        )}
      </dialog>
    </div>
  );
}
