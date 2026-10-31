import { useEffect, useId, useRef, useState } from 'react';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { AdminNav } from './AdminNav.tsx';

/** Where the side menu shows and "Menú" goes (admin.css). */
const SIDE_MENU_QUERY = '(min-width: 860px)';

interface AdminMenuPanelProps {
  signingOut: boolean;
  onSignOut: () => void;
}

/**
 * Under 860 px the menu waits behind "Menú" (E8a navigation): a side panel as a modal dialog, like the store's menu.
 * It keeps the focus inside, Escape closes it and gives the focus back to "Menú", and choosing a link closes it.
 */
export function AdminMenuPanel({ signingOut, onSignOut }: AdminMenuPanelProps) {
  const [isOpen, setOpen] = useState(false);
  const titleId = useId();
  const opener = useRef<HTMLButtonElement>(null);

  function close() {
    setOpen(false);
  }

  // A window that grows to the side menu's width while the panel is open closes it: "Menú", where the focus would
  // return, is not shown there.
  useEffect(() => {
    if (!isOpen || typeof window.matchMedia !== 'function') {
      return undefined;
    }
    const wide = window.matchMedia(SIDE_MENU_QUERY);
    function change() {
      if (wide.matches) {
        setOpen(false);
      }
    }
    wide.addEventListener('change', change);
    return () => {
      wide.removeEventListener('change', change);
    };
  }, [isOpen]);

  return (
    <>
      <button
        ref={opener}
        type="button"
        className="admin-menu-btn"
        aria-haspopup="dialog"
        onClick={() => {
          setOpen(true);
        }}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 7h16M4 12h16M4 17h16" />
        </svg>
        {esAdmin.menu}
      </button>
      <PanelDialog
        open={isOpen}
        from="left"
        className="admin-menu-panel"
        labelledBy={titleId}
        onDismiss={close}
        onClosed={() => opener.current?.focus()}
      >
        <header className="admin-menu-head">
          <h2 id={titleId}>{esAdmin.title}</h2>
          <button type="button" className="icon-btn" aria-label={esAdmin.closeMenu} onClick={close}>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </header>
        <AdminNav signingOut={signingOut} onSignOut={onSignOut} onChoose={close} />
      </PanelDialog>
    </>
  );
}
