import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useEffectEvent,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useLocation, useNavigate } from 'react-router';
import { Toast } from '../../components/motion/Toast.tsx';
import { usePrefersReducedMotion } from '../../components/motion/usePrefersReducedMotion.ts';
import { es } from '../../i18n/es.ts';
import { duration } from '../../styles/motion.ts';
import { signOut } from './accountApi.ts';
import { forgetAccount } from './forgetAccount.ts';
import { isAccountAreaPath } from './paths.ts';

/**
 * T-E7-66, the steps of "Salir" (UC-19):
 * - `pending`: the sign-out request is on its way; "Salir" says "Cerrando sesión…" and the other links fade.
 * - `leaving`: the request answered; for --dur-panel the header's list closes upwards, the account icon loses its dot
 *   and an account page fades out. Then, from an account page, the store goes home, and only once home shows is the
 *   account forgotten (before, the account page would send the visitor to sign in); the goodbye drops in under the
 *   header and the flow is `idle` again.
 */
export type SignOutPhase = 'idle' | 'pending' | 'leaving';

interface SignOutFlow {
  phase: SignOutPhase;
  start: () => void;
  noticeShown: boolean;
  hideNotice: () => void;
}

const SignOutContext = createContext<SignOutFlow | null>(null);

const LEAVE_MS = duration.panel * 1000;
/** How long the goodbye notice stays, as the progress bar under it shows. */
export const NOTICE_MS = 3000;

export function SignOutProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const reduced = usePrefersReducedMotion();
  const [phase, setPhase] = useState<SignOutPhase>('idle');
  const [noticeShown, setNoticeShown] = useState(false);

  // The account is forgotten whether the request worked or not, as before (UC-19): this browser stops showing it.
  const request = useMutation({
    mutationFn: signOut,
    onSettled: () => {
      setPhase('leaving');
    },
  });
  const { mutate } = request;

  const start = useCallback(() => {
    if (phase !== 'idle') {
      return;
    }
    setPhase('pending');
    mutate();
  }, [phase, mutate]);

  const goAway = useEffectEvent(async () => {
    if (isAccountAreaPath(pathname)) {
      // Committed at once, not as a transition, so no account page is on screen when the account is forgotten.
      await navigate('/', { flushSync: true });
    }
    forgetAccount(queryClient);
    setPhase('idle');
    setNoticeShown(true);
  });

  useEffect(() => {
    if (phase !== 'leaving') {
      return undefined;
    }
    // Under reduced motion nothing moves, so nothing is waited for.
    const timer = window.setTimeout(
      () => {
        void goAway();
      },
      reduced ? 0 : LEAVE_MS,
    );
    return () => {
      window.clearTimeout(timer);
    };
  }, [phase, reduced]);

  useEffect(() => {
    if (!noticeShown) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setNoticeShown(false);
    }, NOTICE_MS);
    return () => {
      window.clearTimeout(timer);
    };
  }, [noticeShown]);

  const hideNotice = useCallback(() => {
    setNoticeShown(false);
  }, []);

  const flow = useMemo(() => ({ phase, start, noticeShown, hideNotice }), [phase, start, noticeShown, hideNotice]);
  return <SignOutContext value={flow}>{children}</SignOutContext>;
}

/** The sign-out flow of the store layout; every "Salir" of the header, the phone menu and the account area uses it. */
export function useSignOutFlow(): SignOutFlow {
  const flow = useContext(SignOutContext);
  if (flow === null) {
    throw new Error('useSignOutFlow needs a SignOutProvider (StoreLayout)');
  }
  return flow;
}

/** True from the moment "Salir" is pressed until the account is forgotten. */
export function isSigningOut(phase: SignOutPhase): boolean {
  return phase !== 'idle';
}

/** True while the account's things leave the screen (the header's list, its icon's dot, an account page). */
export function isLeaving(phase: SignOutPhase): boolean {
  return phase === 'leaving';
}

/** The goodbye under the header (T-E7-66): a pill that drops in and goes after NOTICE_MS, with a thin bar that runs out. */
export function SignOutNotice() {
  const { phase, noticeShown, hideNotice } = useSignOutFlow();
  // The status region joins the page when "Salir" is pressed, before the goodbye enters it, so screen readers announce
  // it; until then the page keeps a single status region of its own.
  const [needed, setNeeded] = useState(false);
  if (isSigningOut(phase) && !needed) {
    setNeeded(true);
  }
  if (!needed) {
    return null;
  }
  return (
    <div className="session-notice">
      <Toast open={noticeShown} closeLabel={es.account.closeNotice} onClose={hideNotice}>
        <span data-kaomoji={es.kaomoji.bye}>{es.account.signedOutNotice}</span>
      </Toast>
    </div>
  );
}
