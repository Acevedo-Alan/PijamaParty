import { createContext, useContext, useMemo, useRef, useState, type ReactNode } from 'react';

interface CartPanelState {
  isOpen: boolean;
  /** How many products were added on this page view; the cart button bounces each time it grows. */
  added: number;
  open: () => void;
  close: () => void;
  /** The dialog closed (also with Escape): the focus goes back to what opened it, if it is still on the page. */
  closed: () => void;
  /** UC-08: after a successful add the panel opens by itself, the counter bounces and the phone vibrates. */
  showAdded: () => void;
}

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
  const opener = useRef<HTMLElement | null>(null);

  const state = useMemo<CartPanelState>(() => {
    function open() {
      opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      setOpen(true);
    }
    return {
      isOpen,
      added,
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
        open();
        setAdded((count) => count + 1);
        vibrate();
      },
    };
  }, [isOpen, added]);

  return <CartPanelContext value={state}>{children}</CartPanelContext>;
}

export function useCartPanel(): CartPanelState {
  const state = useContext(CartPanelContext);
  if (state === null) {
    throw new Error('useCartPanel needs a CartPanelProvider');
  }
  return state;
}
