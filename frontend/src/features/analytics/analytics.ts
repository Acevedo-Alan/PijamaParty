/**
 * Google Analytics 4 and Meta Pixel (RNF-25), loaded only after the visitor accepts (T-E1-23). Nothing here runs
 * before {@link loadAnalytics}; every call before that, or for a tool without an id, does nothing.
 */

type Command = (...args: unknown[]) => void;

interface PixelQueue extends Command {
  callMethod?: Command;
  queue: unknown[][];
  push: PixelQueue;
  loaded: boolean;
  version: string;
}

declare global {
  interface Window {
    dataLayer?: unknown[];
    gtag?: Command;
    fbq?: PixelQueue;
    _fbq?: PixelQueue;
  }
}

export interface AnalyticsIds {
  ga4?: string;
  pixel?: string;
}

const GA4_ID = /^G-[A-Z0-9]{4,20}$/;
const PIXEL_ID = /^\d{5,20}$/;

/** The ids of the build (VITE_GA4_MEASUREMENT_ID, VITE_META_PIXEL_ID); a missing or malformed one is left out. */
export function analyticsIds(env: Record<string, unknown> = import.meta.env): AnalyticsIds {
  const ga4 = env.VITE_GA4_MEASUREMENT_ID;
  const pixel = env.VITE_META_PIXEL_ID;
  return {
    ...(typeof ga4 === 'string' && GA4_ID.test(ga4) ? { ga4 } : {}),
    ...(typeof pixel === 'string' && PIXEL_ID.test(pixel) ? { pixel } : {}),
  };
}

export function hasAnalytics(ids: AnalyticsIds): boolean {
  return ids.ga4 !== undefined || ids.pixel !== undefined;
}

let loaded: AnalyticsIds | null = null;

function addScript(src: string) {
  const script = document.createElement('script');
  script.async = true;
  script.src = src;
  document.head.append(script);
}

function loadGa4(id: string) {
  window.dataLayer = window.dataLayer ?? [];
  const dataLayer = window.dataLayer;
  // gtag.js reads the arguments object itself from the data layer, not an array of them.
  window.gtag = function gtag() {
    // eslint-disable-next-line prefer-rest-params
    dataLayer.push(arguments);
  };
  window.gtag('js', new Date());
  // The app sends each page view itself, without query string (see trackPageView).
  window.gtag('config', id, { send_page_view: false });
  addScript(`https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(id)}`);
}

function loadPixel(id: string) {
  // The queue of Meta's snippet: calls wait in it until fbevents.js takes over.
  const fbq = Object.assign(
    (...args: unknown[]) => {
      if (fbq.callMethod === undefined) {
        fbq.queue.push(args);
      } else {
        fbq.callMethod(...args);
      }
    },
    { queue: [] as unknown[][], loaded: true, version: '2.0' },
  ) as PixelQueue;
  fbq.push = fbq;
  window.fbq = fbq;
  window._fbq = fbq;
  fbq('init', id);
  addScript('https://connect.facebook.net/en_US/fbevents.js');
}

/** Loads the configured tools once, after consent. */
export function loadAnalytics(ids: AnalyticsIds): void {
  if (loaded !== null) {
    return;
  }
  loaded = ids;
  if (ids.ga4 !== undefined) {
    loadGa4(ids.ga4);
  }
  if (ids.pixel !== undefined) {
    loadPixel(ids.pixel);
  }
}

/** True once the visitor accepted and the tools were loaded on this page. */
export function analyticsLoaded(): boolean {
  return loaded !== null;
}

/** A page view of the address without query string or fragment, which may hold search texts or tokens. */
export function trackPageView(path: string, title: string): void {
  if (loaded === null) {
    return;
  }
  const location = `${window.location.origin}${path}`;
  window.gtag?.('event', 'page_view', { page_location: location, page_path: path, page_title: title });
  window.fbq?.('track', 'PageView');
}

const CENTS_PER_PESO = 100;

/** Event amounts go in pesos, with currency ARS (RNF-25). */
export function pesos(cents: number): number {
  return cents / CENTS_PER_PESO;
}

/**
 * A GA4 event and, when it has one, its Meta Pixel counterpart (RNF-25). The Pixel's {@code eventId} lets Meta drop a
 * repetition of the same event.
 */
export function trackEvent(ga4: { name: string; params: Record<string, unknown> }, pixel?: {
  name: string;
  params: Record<string, unknown>;
  eventId?: string;
}): void {
  if (loaded === null) {
    return;
  }
  window.gtag?.('event', ga4.name, ga4.params);
  if (pixel === undefined) {
    return;
  }
  if (pixel.eventId === undefined) {
    window.fbq?.('track', pixel.name, pixel.params);
  } else {
    window.fbq?.('track', pixel.name, pixel.params, { eventID: pixel.eventId });
  }
}

/** The cookies both tools set on this site (RNF-25), deleted when the visitor revokes consent. */
export function clearAnalyticsCookies(doc: Document = document): void {
  const names = doc.cookie
    .split(';')
    .map((cookie) => cookie.split('=')[0]?.trim() ?? '')
    .filter((name) => name === '_ga' || name.startsWith('_ga_') || name === '_fbp' || name === '_gid');
  const host = doc.location.hostname;
  const domains = ['', host, `.${host}`, `.${host.split('.').slice(-2).join('.')}`];
  for (const name of names) {
    for (const domain of domains) {
      doc.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/${domain === '' ? '' : `; domain=${domain}`}`;
    }
  }
}

/** Forgets what was loaded: only for tests, which share one page. */
export function resetAnalyticsForTests(): void {
  loaded = null;
  delete window.gtag;
  delete window.fbq;
  delete window._fbq;
  delete window.dataLayer;
}
