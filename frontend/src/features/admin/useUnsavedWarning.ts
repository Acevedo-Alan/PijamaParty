import { useEffect } from 'react';
import { useBlocker } from 'react-router';

/** Where the back office sends the administrator when the session ends: no question can keep them here. */
const SIGN_IN = '/admin/ingresar';

/**
 * UC-07 (T-E8-09, decided on 2026-11-04): while `unsaved`, leaving the page or changing its address (its filters)
 * asks `question` first, as the back office's other confirmations do, and so do reloading and closing the tab (the
 * browser's own question, whose words it chooses). The app's own redirect to sign in, after the session expired or
 * was closed, never asks.
 */
export function useUnsavedWarning(unsaved: boolean, question: string) {
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      unsaved &&
      nextLocation.pathname !== SIGN_IN &&
      (currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search),
  );

  useEffect(() => {
    if (blocker.state !== 'blocked') {
      return;
    }
    if (window.confirm(question)) {
      blocker.proceed();
    } else {
      blocker.reset();
    }
  }, [blocker, question]);

  useEffect(() => {
    if (!unsaved) {
      return;
    }
    function warn(event: BeforeUnloadEvent) {
      // Every supported browser asks on preventDefault (Chromium since 119); the legacy returnValue is deprecated.
      event.preventDefault();
    }
    window.addEventListener('beforeunload', warn);
    return () => {
      window.removeEventListener('beforeunload', warn);
    };
  }, [unsaved]);
}
