import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Toast } from '../../components/motion/Toast.tsx';
import { es } from '../../i18n/es.ts';
import { NOTICE_MS } from './signOutFlow.tsx';
import { firstName, useCustomer } from './useCustomer.ts';

/**
 * T-E7-72: the welcome under the header once the customer signs in, the twin of the goodbye of "Salir" (T-E7-66). A
 * password sign-in greets at once; a sign-in with Google or Facebook leaves the store, so "Continuar con …" marks the
 * browser tab and the first time the store knows the visitor again, it greets if signed in.
 */
interface Welcome {
  /** The first name to greet, from the first welcome on; null until then. */
  name: string | null;
  open: boolean;
  greet: (name: string) => void;
  hide: () => void;
}

const WelcomeContext = createContext<Welcome | null>(null);

const PROVIDER_MARK = 'pijamaparty.welcome-after-provider';

/** Called as the visitor leaves for a provider; without storage (a private window) there is simply no welcome. */
export function markProviderSignIn(): void {
  try {
    sessionStorage.setItem(PROVIDER_MARK, '');
  } catch {
    // No welcome then.
  }
}

/** Whether a provider sign-in was started in this tab; the mark goes either way, so it greets once. */
function takeProviderMark(): boolean {
  try {
    const marked = sessionStorage.getItem(PROVIDER_MARK) !== null;
    sessionStorage.removeItem(PROVIDER_MARK);
    return marked;
  } catch {
    return false;
  }
}

export function WelcomeProvider({ children }: { children: ReactNode }) {
  const customer = useCustomer();
  // Each welcome has its own id, so a second sign-in in the same visit greets again.
  const [notice, setNotice] = useState<{ name: string; id: number } | null>(null);
  const [shownId, setShownId] = useState<number | null>(null);
  const open = notice !== null && shownId === notice.id;

  const greet = useCallback((fullName: string) => {
    setNotice((last) => ({ name: firstName(fullName), id: (last?.id ?? 0) + 1 }));
  }, []);

  // The return from a provider, once the store knows whether the visitor is signed in. The mark is read in the
  // timer, so an effect undone at once (React's strict mode) neither loses nor doubles the welcome.
  useEffect(() => {
    if (customer === undefined) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      if (takeProviderMark() && customer !== null) {
        greet(customer.name);
      }
    }, 0);
    return () => {
      window.clearTimeout(timer);
    };
  }, [customer, greet]);

  // After the commit that put the status region on the page, so screen readers announce what then enters it.
  useEffect(() => {
    if (notice === null) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setShownId(notice.id);
    }, 0);
    return () => {
      window.clearTimeout(timer);
    };
  }, [notice]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setShownId(null);
    }, NOTICE_MS);
    return () => {
      window.clearTimeout(timer);
    };
  }, [open]);

  const hide = useCallback(() => {
    setShownId(null);
  }, []);

  const name = notice?.name ?? null;
  const welcome = useMemo(() => ({ name, open, greet, hide }), [name, open, greet, hide]);
  return <WelcomeContext value={welcome}>{children}</WelcomeContext>;
}

/** The welcome of the store layout; the sign-in page greets through it. */
export function useWelcome(): Welcome {
  const welcome = useContext(WelcomeContext);
  if (welcome === null) {
    throw new Error('useWelcome needs a WelcomeProvider (StoreLayout)');
  }
  return welcome;
}

/** The pill under the header, as the goodbye's: it drops in and goes after NOTICE_MS, with a thin bar that runs out. */
export function WelcomeNotice() {
  const { name, open, hide } = useWelcome();
  if (name === null) {
    return null;
  }
  return (
    <div className="session-notice">
      <Toast open={open} closeLabel={es.account.closeNotice} onClose={hide}>
        <span data-kaomoji={es.kaomoji.welcome}>{es.account.welcomeNotice(name)}</span>
      </Toast>
    </div>
  );
}
