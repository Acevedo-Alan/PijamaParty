import { useId, useRef, useState } from 'react';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { es } from '../../i18n/es.ts';
import { MenuAccount } from '../account/AccountLink.tsx';
import { StoreNavigation } from './StoreNavigation.tsx';

/**
 * The phone menu below 860 px (UC-31): a side panel from the left with the navigation and the account entries. As
 * a modal dialog it keeps the focus inside; Escape, a tap outside or a chosen link closes it, and the focus goes
 * back to the menu button.
 */
export function MobileMenu() {
  const [isOpen, setOpen] = useState(false);
  const button = useRef<HTMLButtonElement>(null);
  const titleId = useId();

  function close() {
    setOpen(false);
  }

  return (
    <>
      <button
        ref={button}
        type="button"
        className="icon-btn menu-toggle"
        aria-label={es.header.openMenu}
        aria-haspopup="dialog"
        onClick={() => {
          setOpen(true);
        }}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M3 6h18M3 12h18M3 18h18" strokeLinecap="round" />
        </svg>
      </button>
      <PanelDialog
        open={isOpen}
        from="left"
        className="menu-panel"
        labelledBy={titleId}
        onDismiss={close}
        onClosed={() => {
          button.current?.focus();
        }}
      >
        <div className="menu-inner">
          <header className="menu-head">
            <p className="menu-brand">{es.store.name}</p>
            <h2 id={titleId} className="menu-title">
              {es.header.menuTitle}
            </h2>
            <button type="button" className="icon-btn" aria-label={es.header.closeMenu} onClick={close}>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </header>
          <nav className="menu-body" aria-label={es.header.navigation}>
            <StoreNavigation className="menu-links" onChoose={close} />
          </nav>
          <section className="menu-foot" aria-label={es.header.account}>
            <MenuAccount onChoose={close} />
          </section>
        </div>
      </PanelDialog>
    </>
  );
}
