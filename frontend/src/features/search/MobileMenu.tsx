import { m } from 'motion/react';
import { useId, useRef, useState } from 'react';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { staggered, useEntranceMotion } from '../../components/motion/useActionMotion.ts';
import { useMediaQuery } from '../../components/useMediaQuery.ts';
import { es } from '../../i18n/es.ts';
import { MenuAccount } from '../account/AccountLink.tsx';
import { SocialIconLinks } from './SiteFooter.tsx';
import { StoreNavigationLink, useStoreLinks } from './StoreNavigation.tsx';

/** Below this width the menu is a sheet from the bottom; from it, a panel from the left (UC-31). */
const SHEET_QUERY = '(max-width: 699px)';

/**
 * What the open menu shows: the round close button, then the sheet with the navigation, the networks and the
 * account entries. It mounts with the panel, so each navigation link comes in after the other while it opens
 * (T-E7-17): rising with the sheet, or from the left with the side panel.
 */
function MenuContents({ titleId, sheet, onClose }: { titleId: string; sheet: boolean; onClose: () => void }) {
  const entrance = useEntranceMotion(sheet ? 'below' : 'left');
  const links = useStoreLinks();

  return (
    <div className="menu-inner">
      <button type="button" className="menu-close" aria-label={es.header.closeMenu} onClick={onClose}>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
      <div className="menu-sheet">
        <h2 id={titleId} className="visually-hidden">
          {es.header.menuTitle}
        </h2>
        <nav className="menu-body" aria-label={es.header.navigation}>
          <ul className="menu-links">
            {links.map((link, index) => (
              <m.li key={link.label} {...staggered(entrance, index)}>
                <StoreNavigationLink link={link} onChoose={onClose} />
              </m.li>
            ))}
          </ul>
          <SocialIconLinks className="menu-social" />
        </nav>
        <section className="menu-foot" aria-label={es.header.account}>
          <MenuAccount onChoose={onClose} />
        </section>
      </div>
    </div>
  );
}

/**
 * The phone menu below 860 px (UC-31): a sheet that rises from the bottom below 700 px, a panel from the left from
 * there, with the navigation, the networks and the account entries. As a modal dialog it keeps the focus inside;
 * Escape, a tap outside or a chosen link closes it, and the focus goes back to the menu button.
 */
export function MobileMenu() {
  const [isOpen, setOpen] = useState(false);
  const button = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const sheet = useMediaQuery(SHEET_QUERY);

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
        from={sheet ? 'bottom' : 'left'}
        className={sheet ? 'menu-panel menu-panel-sheet' : 'menu-panel'}
        labelledBy={titleId}
        onDismiss={close}
        onClosed={() => {
          button.current?.focus();
        }}
      >
        <MenuContents titleId={titleId} sheet={sheet} onClose={close} />
      </PanelDialog>
    </>
  );
}
