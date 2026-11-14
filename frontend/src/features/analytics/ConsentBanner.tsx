import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { focusPage } from '../../components/focusPage.ts';
import { es } from '../../i18n/es.ts';
import { COOKIES_PATH } from '../help/FaqLink.tsx';
import {
  analyticsIds,
  analyticsLoaded,
  clearAnalyticsCookies,
  hasAnalytics,
  loadAnalytics,
  trackPageView,
} from './analytics.ts';
import { readConsent, saveConsent, type ConsentChoice } from './consent.ts';
import './analytics.css';

const messages = es.consent;

/** Opens the banner again from the footer's "Cookies" link, which gets the focus back after the choice (T-E6-25). */
const reopenListeners = new Set<(opener: HTMLElement | null) => void>();

export function reopenConsent(opener: HTMLElement | null = null): void {
  for (const listener of reopenListeners) {
    listener(opener);
  }
}

/** Reloads the page; replaced in tests, where jsdom cannot navigate. */
export const pageReload = {
  run: (): void => {
    window.location.reload();
  },
};

/**
 * The consent banner and the analytics loader of the public pages (RNF-25, T-E1-23): nothing loads before
 * "Aceptar", the choice lasts 12 months, and each route change is a page view once loaded.
 */
export function Analytics() {
  const ids = analyticsIds();
  const enabled = hasAnalytics(ids);
  const [choice, setChoice] = useState<ConsentChoice | null>(() => (enabled ? readConsent() : null));
  const [open, setOpen] = useState(() => enabled && choice === null);
  const { pathname } = useLocation();
  const region = useRef<HTMLElement>(null);
  const opener = useRef<HTMLElement | null>(null);
  // T-E6-25: each reopening from the footer moves the focus to the banner, also when it is already open.
  const [focusRequest, setFocusRequest] = useState(0);

  useEffect(() => {
    const listener = (from: HTMLElement | null) => {
      opener.current = from;
      setOpen(true);
      setFocusRequest((count) => count + 1);
    };
    reopenListeners.add(listener);
    return () => {
      reopenListeners.delete(listener);
    };
  }, []);

  useEffect(() => {
    if (focusRequest > 0) {
      region.current?.focus();
    }
  }, [focusRequest]);

  useEffect(() => {
    if (enabled && choice === 'accepted') {
      loadAnalytics(ids);
    }
    // The ids come from the build and never change while the page lives.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, choice]);

  useEffect(() => {
    if (choice === 'accepted' && analyticsLoaded()) {
      // After the page renders, so the title is the new one.
      const timer = setTimeout(() => {
        trackPageView(pathname, document.title);
      }, 0);
      return () => {
        clearTimeout(timer);
      };
    }
    return undefined;
  }, [pathname, choice]);

  if (!enabled || !open) {
    return null;
  }

  function decide(next: ConsentChoice) {
    const revoked = choice === 'accepted' && next === 'rejected';
    saveConsent(next);
    setChoice(next);
    setOpen(false);
    // T-E6-25, WCAG 2.4.3: the pressed button goes away with the banner, so the focus does not fall to <body>.
    const from = opener.current;
    opener.current = null;
    if (from?.isConnected === true) {
      from.focus();
    } else {
      focusPage();
    }
    if (revoked) {
      clearAnalyticsCookies();
      if (analyticsLoaded()) {
        pageReload.run();
      }
    }
  }

  return (
    <section ref={region} className="consent" aria-label={messages.title} tabIndex={-1}>
      <div className="wrap consent-inner">
        <p>
          {messages.text}
          {choice !== null && <span className="consent-current"> {messages.current[choice]}</span>}{' '}
          <Link to={COOKIES_PATH}>{messages.policyLink}</Link>
        </p>
        <div className="consent-actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              decide('rejected');
            }}
          >
            {messages.reject}
          </button>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              decide('accepted');
            }}
          >
            {messages.accept}
          </button>
        </div>
      </div>
    </section>
  );
}

/** The footer's link to change the analytics choice (RNF-25); a build without analytics has nothing to change. */
export function ConsentFooterLink() {
  if (!hasAnalytics(analyticsIds())) {
    return null;
  }
  return (
    <button
      type="button"
      className="link-btn"
      onClick={(event) => {
        reopenConsent(event.currentTarget);
      }}
    >
      {messages.footerLink}
    </button>
  );
}
