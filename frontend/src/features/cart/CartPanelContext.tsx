import { createContext, useContext, useMemo, useRef, useState, type ReactNode } from 'react';

interface CartPanelState {
  isOpen: boolean;
  /** How many products were added on this page view; the cart button bounces each time it grows. */
  added: number;
  /** The same count, 100 ms earlier: the bag and the counter bounce while the add button's tick is drawn. */
  bumped: number;
  open: () => void;
  close: () => void;
  /** The dialog closed (also with Escape): the focus goes back to what opened it, if it is still on the page. */
  closed: () => void;
  /** UC-08: after a successful add the phone vibrates, the bag and the counter bounce, and the panel opens by itself. */
  showAdded: () => void;
}

/** E7 motion: when, after a successful add, the cart icon bounces and when the panel opens. */
const BUMP_AFTER_MS = 350;
const PANEL_AFTER_MS = 450;

const CartPanelContext = createContext<CartPanelState | null>(null);

/** A light vibration on phones that support it; elsewhere nothing happens and nothing else depends on it. */
function vibrate() {
  if (typeof navigator.vibrate === 'function') {
    navigator.vibrate(18);
  }
}

/** Whether the side panel of the cart is open, shared by the header button, the product page and the panel. */
export function CartPanelProvider({ children }: { children: ReactNode }) {
  const [isOpen, setOpen] = useState(false);
  const [added, setAdded] = useState(0);
  const [bumped, setBumped] = useState(0);
  const opener = useRef<HTMLElement | null>(null);

  const state = useMemo<CartPanelState>(() => {
    function open() {
      opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      setOpen(true);
    }
    return {
      isOpen,
      added,
      bumped,
      open,
      close() {
        setOpen(false);
      },
      // Only once the modal dialog is closed can the page behind it take the focus again.
      closed() {
        setOpen(false);
        const previous = opener.current;
        opener.current = null;
        if (previous?.isConnected === true) {
          previous.focus();
        }
      },
      showAdded() {
        vibrate();
        // The add button's tick draws first: the bag and the counter bounce at 350 ms, the panel opens at 450 ms.
        setTimeout(() => {
          setBumped((count) => count + 1);
        }, BUMP_AFTER_MS);
        setTimeout(() => {
          open();
          setAdded((count) => count + 1);
        }, PANEL_AFTER_MS);
      },
    };
  }, [isOpen, added, bumped]);

  return <CartPanelContext value={state}>{children}</CartPanelContext>;
}

export function useCartPanel(): CartPanelState {
  const state = useContext(CartPanelContext);
  if (state === null) {
    throw new Error('useCartPanel needs a CartPanelProvider');
  }
  return state;
}
