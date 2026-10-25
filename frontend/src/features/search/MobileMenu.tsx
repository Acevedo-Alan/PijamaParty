import { m } from 'motion/react';
import { useId, useRef, useState } from 'react';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { staggered, useEntranceMotion } from '../../components/motion/useActionMotion.ts';
import { es } from '../../i18n/es.ts';
import { MenuAccount } from '../account/AccountLink.tsx';
import { StoreLogo } from './StoreLogo.tsx';
import { StoreNavigationLink, useStoreLinks } from './StoreNavigation.tsx';

/**
 * What the open menu shows. It mounts with the panel, so the logo and then each navigation link come in from the
 * left one after another while it slides open (T-E7-17).
 */
function MenuContents({ titleId, onClose }: { titleId: string; onClose: () => void }) {
  const entrance = useEntranceMotion('left');
  const links = useStoreLinks();

  return (
    <div className="menu-inner">
      <header className="menu-head">
        <m.div className="menu-brand" {...entrance}>
          <StoreLogo />
        </m.div>
        <h2 id={titleId} className="menu-title">
          {es.header.menuTitle}
        </h2>
        <button type="button" className="icon-btn" aria-label={es.header.closeMenu} onClick={onClose}>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </header>
      <nav className="menu-body" aria-label={es.header.navigation}>
        <ul className="menu-links">
          {links.map((link, index) => (
            <m.li key={link.label} {...staggered(entrance, index + 1)}>
              <StoreNavigationLink link={link} onChoose={onClose} />
            </m.li>
          ))}
        </ul>
      </nav>
      <section className="menu-foot" aria-label={es.header.account}>
        <MenuAccount onChoose={onClose} />
      </section>
    </div>
  );
}

/**
 * The phone menu below 860 px (UC-31): a side panel from the left with the store's logo, the navigation and the
 * account entries. As a modal dialog it keeps the focus inside; Escape, a tap outside or a chosen link closes it,
 * and the focus goes back to the menu button.
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
        <MenuContents titleId={titleId} onClose={close} />
      </PanelDialog>
    </>
  );
}
