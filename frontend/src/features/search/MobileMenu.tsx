import { m } from 'motion/react';
import { useEffect, useId, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { staggered, useEntranceMotion } from '../../components/motion/useActionMotion.ts';
import { es } from '../../i18n/es.ts';
import { MenuAccountLinks, PersonIcon, SIGN_IN_PATH } from '../account/AccountLink.tsx';
import { useCustomer } from '../account/useCustomer.ts';
import { EMPTY_CART, unitCount } from '../cart/cartApi.ts';
import { BagIcon } from '../cart/CartButton.tsx';
import { useCartPanel } from '../cart/CartPanelContext.tsx';
import { useCart } from '../cart/useCart.ts';
import { FAQ_PATH, SIZE_GUIDES_PATH } from '../help/FaqLink.tsx';
import { STORE_CONTACT, whatsAppUrl } from '../help/storeContact.ts';
import { SocialIconLinks } from './SiteFooter.tsx';
import { StoreNavigationLink, useStoreLinks, type StoreLink } from './StoreNavigation.tsx';
import { useShopCategories } from './useShopCategories.ts';

/** The menu's first level, or one of the levels that slide in over it (T-E7-39). */
type Level = 'root' | 'all' | 'account';
type SubLevel = Exclude<Level, 'root'>;

function Chevron({ direction }: { direction: 'left' | 'right' }) {
  return (
    <svg className="menu-chevron" viewBox="0 0 24 24" aria-hidden="true">
      <path d={direction === 'right' ? 'M9 6l6 6-6 6' : 'M15 6l-6 6 6 6'} />
    </svg>
  );
}

/** A tape measure, in line like the bag. */
function TapeIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3" y="8" width="18" height="8" rx="2" />
      <path d="M7 8v3M11 8v4M15 8v3M19 8v2" strokeLinecap="round" />
    </svg>
  );
}

interface OpenLevel {
  /** Opens a level; the button that asked is where the focus returns from it. */
  open: (level: SubLevel, opener: HTMLButtonElement) => void;
}

/**
 * The account first, then "Mi carrito" with its units and "Guía de talles", smaller than the navigation, each after its
 * icon (T-E7-37, T-E7-39). Signed out the account is "Ingresar", the sign-in page (which offers "Crear cuenta"); with a
 * session it is "Mi cuenta", which opens its level.
 */
function Shortcuts({ onChoose, onOpenCart, open }: { onChoose: () => void; onOpenCart: () => void } & OpenLevel) {
  const count = unitCount(useCart().data ?? EMPTY_CART);
  const customer = useCustomer();
  return (
    <section className="menu-shortcuts" aria-label={es.header.shortcuts}>
      <ul>
        {customer !== undefined && (
          <li>
            {customer === null ? (
              <Link to={SIGN_IN_PATH} onClick={onChoose}>
                <PersonIcon />
                {es.account.signInLink}
              </Link>
            ) : (
              <button
                type="button"
                onClick={(event) => {
                  open('account', event.currentTarget);
                }}
              >
                <PersonIcon />
                {es.account.myAccountLink}
                <Chevron direction="right" />
              </button>
            )}
          </li>
        )}
        <li>
          <button type="button" aria-haspopup="dialog" aria-label={es.header.cartShortcut(count)} onClick={onOpenCart}>
            <BagIcon />
            {es.cart.title}
            {count > 0 && (
              <span className="menu-count" aria-hidden="true">
                {count}
              </span>
            )}
          </button>
        </li>
        <li>
          <Link to={SIZE_GUIDES_PATH} onClick={onChoose}>
            <TapeIcon />
            {es.footer.sizeGuides}
          </Link>
        </li>
      </ul>
    </section>
  );
}

/** "¿Necesitás ayuda?" with the FAQ and, once the business sent its number (D-15), WhatsApp; then the networks. */
function MenuHelp({ onChoose }: { onChoose: () => void }) {
  const titleId = useId();
  const number = STORE_CONTACT.whatsAppNumber;
  return (
    <section className="menu-help" aria-labelledby={titleId}>
      <p id={titleId}>{es.header.help}</p>
      <ul>
        <li>
          <Link to={FAQ_PATH} onClick={onChoose}>
            {es.faq.title}
          </Link>
        </li>
        {number !== undefined && (
          <li>
            <a href={whatsAppUrl(number)} target="_blank" rel="noopener noreferrer">
              {es.contact.whatsAppButton}
            </a>
          </li>
        )}
      </ul>
      <SocialIconLinks className="menu-social" />
    </section>
  );
}

/** The "Todo" level (T-E7-39): every product, then the active categories by position, the one shown marked. */
function AllLevel({ all, onChoose }: { all: StoreLink; onChoose: () => void }) {
  const categories = useShopCategories().data ?? [];
  const { pathname } = useLocation();
  return (
    <>
      <h3 className="menu-level-title">{all.label}</h3>
      <ul className="menu-links menu-sub-links">
        <li>
          <StoreNavigationLink link={{ ...all, label: es.header.seeAll }} onChoose={onChoose} />
        </li>
        {categories.map((category) => {
          const path = `/categoria/${category.slug}`;
          return (
            <li key={category.slug}>
              <Link to={path} aria-current={pathname === path ? 'page' : undefined} onClick={onChoose}>
                {category.name}
              </Link>
            </li>
          );
        })}
      </ul>
    </>
  );
}

/** The account level (T-E7-39): the greeting and the account links, as the header's list shows them. */
function AccountLevel({ onChoose }: { onChoose: () => void }) {
  const customer = useCustomer();
  return (
    <>
      <h3 className="menu-level-title">{es.account.myAccountLink}</h3>
      {customer !== undefined && customer !== null && <MenuAccountLinks customer={customer} onChoose={onChoose} />}
    </>
  );
}

/**
 * What the open menu shows (T-E7-39): a top row with the title, or "Volver" inside a level, and the close button; then
 * the first level with the navigation, the shortcuts and the help, and the level that slides in over it from the right
 * ("Todo" with the categories, or "Mi cuenta"). It mounts with the panel, so it always opens at its first level, and
 * each navigation link comes in from the left after the other while the panel slides in (T-E7-17).
 */
function MenuContents({
  titleId,
  onClose,
  onOpenCart,
}: {
  titleId: string;
  onClose: () => void;
  onOpenCart: () => void;
}) {
  const entrance = useEntranceMotion('left');
  const links = useStoreLinks();
  // "Todo": the link without a filter.
  const all = links.find((link) => link.search === '');
  const hasCategories = (useShopCategories().data ?? []).length > 0;
  const [level, setLevel] = useState<Level>('root');
  // The level last opened stays in place while it slides out, so "Volver" shows it leaving.
  const [subLevel, setSubLevel] = useState<SubLevel>('all');
  const opener = useRef<HTMLButtonElement | null>(null);
  const back = useRef<HTMLButtonElement>(null);
  // The focus moves once the level it goes to is no longer inert, after the render that changes it; "Volver" only
  // exists from that render on.
  const focusNext = useRef<'back' | HTMLElement | null>(null);

  useEffect(() => {
    const target = focusNext.current === 'back' ? back.current : focusNext.current;
    focusNext.current = null;
    target?.focus();
  }, [level]);

  function open(next: SubLevel, from: HTMLButtonElement) {
    opener.current = from;
    focusNext.current = 'back';
    setSubLevel(next);
    setLevel(next);
  }

  function goBack() {
    focusNext.current = opener.current;
    setLevel('root');
  }

  const inLevel = level !== 'root';
  return (
    <div className="menu-sheet">
      <div className="menu-head">
        <h2 id={titleId} className={inLevel ? 'visually-hidden' : 'menu-title'}>
          {es.header.menuTitle}
        </h2>
        {inLevel && (
          <button ref={back} type="button" className="menu-back" aria-label={es.header.backLabel} onClick={goBack}>
            <Chevron direction="left" />
            {es.header.back}
          </button>
        )}
        <button type="button" className="menu-close" aria-label={es.header.closeMenu} onClick={onClose}>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>
      <div className="menu-levels" data-level={level}>
        <div className="menu-level menu-level-root" inert={inLevel}>
          <nav aria-label={es.header.navigation}>
            <ul className="menu-links">
              {links.map((link, index) => (
                <m.li key={link.label} {...staggered(entrance, index)}>
                  {link === all && hasCategories ? (
                    <button
                      type="button"
                      className="menu-drill"
                      onClick={(event) => {
                        open('all', event.currentTarget);
                      }}
                    >
                      {link.label}
                      <Chevron direction="right" />
                    </button>
                  ) : (
                    <StoreNavigationLink link={link} onChoose={onClose} />
                  )}
                </m.li>
              ))}
            </ul>
          </nav>
          <Shortcuts onChoose={onClose} onOpenCart={onOpenCart} open={open} />
          <MenuHelp onChoose={onClose} />
        </div>
        <div className="menu-level menu-level-sub" inert={!inLevel}>
          {subLevel === 'account' && <AccountLevel onChoose={onClose} />}
          {subLevel === 'all' && all !== undefined && <AllLevel all={all} onChoose={onClose} />}
        </div>
      </div>
    </div>
  );
}

/**
 * The phone menu below 860 px (UC-31, T-E7-39): a full-height panel that slides in from the left, edge to edge, with
 * levels that slide in from the right. As a modal dialog it keeps the focus inside; Escape, a tap outside or a chosen
 * link closes it, and the focus goes back to the menu button. "Mi carrito" opens the cart panel once the menu has
 * closed, so the panel's close returns the focus to the menu button too.
 */
export function MobileMenu() {
  const [isOpen, setOpen] = useState(false);
  const button = useRef<HTMLButtonElement>(null);
  const cartAfterClose = useRef(false);
  const cartPanel = useCartPanel();
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
          if (cartAfterClose.current) {
            cartAfterClose.current = false;
            cartPanel.open();
          }
        }}
      >
        <MenuContents
          titleId={titleId}
          onClose={close}
          onOpenCart={() => {
            cartAfterClose.current = true;
            close();
          }}
        />
      </PanelDialog>
    </>
  );
}
