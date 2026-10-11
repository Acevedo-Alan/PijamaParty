import { afterEach, describe, expect, it } from 'vitest';
import {
  analyticsIds,
  clearAnalyticsCookies,
  loadAnalytics,
  resetAnalyticsForTests,
  trackEvent,
  trackPageView,
} from './analytics.ts';
import { readConsent, saveConsent } from './consent.ts';

const DAY = 24 * 60 * 60 * 1000;

function scripts(): string[] {
  return [...document.head.querySelectorAll('script')].map((script) => script.src);
}

describe('consent storage (RNF-25)', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('given a choice, when read within 12 months, then it is kept, and after that the banner asks again', () => {
    saveConsent('accepted', 0);

    expect(readConsent(364 * DAY)).toBe('accepted');
    expect(readConsent(366 * DAY)).toBeNull();
  });

  it('given nothing or an unreadable value, when read, then there is no choice', () => {
    expect(readConsent()).toBeNull();
    localStorage.setItem('pp-consent', '{not json');
    expect(readConsent()).toBeNull();
    localStorage.setItem('pp-consent', JSON.stringify({ choice: 'maybe', decidedAt: 0 }));
    expect(readConsent(1)).toBeNull();
  });

  it('given a browser that refuses storage, when saving, then nothing breaks', () => {
    const refusing = {
      setItem: () => {
        throw new Error('QuotaExceededError');
      },
    } as unknown as Storage;

    expect(() => {
      saveConsent('rejected', 0, refusing);
    }).not.toThrow();
  });
});

describe('analytics loader (RNF-25, T-E1-23)', () => {
  afterEach(() => {
    resetAnalyticsForTests();
    document.head.querySelectorAll('script').forEach((script) => {
      script.remove();
    });
  });

  it('given build variables, when read, then only well-formed ids are used', () => {
    expect(analyticsIds({ VITE_GA4_MEASUREMENT_ID: 'G-ABC123XYZ', VITE_META_PIXEL_ID: '1234567890' })).toEqual({
      ga4: 'G-ABC123XYZ',
      pixel: '1234567890',
    });
    expect(analyticsIds({ VITE_GA4_MEASUREMENT_ID: 'G-"><script>', VITE_META_PIXEL_ID: '' })).toEqual({});
    expect(analyticsIds({})).toEqual({});
  });

  it('given nothing loaded, when tracking, then nothing is sent', () => {
    trackPageView('/', 'Inicio');
    trackEvent({ name: 'search', params: { search_term: 'pijama' } });

    expect(window.dataLayer).toBeUndefined();
    expect(scripts()).toEqual([]);
  });

  it('given consent, when loaded, then both tools load once without their automatic page view', () => {
    loadAnalytics({ ga4: 'G-ABC123', pixel: '1234567890' });
    loadAnalytics({ ga4: 'G-ABC123', pixel: '1234567890' });

    expect(scripts()).toEqual([
      'https://www.googletagmanager.com/gtag/js?id=G-ABC123',
      'https://connect.facebook.net/en_US/fbevents.js',
    ]);
    const commands = (window.dataLayer ?? []).map((entry) => [...(entry as IArguments)] as unknown[]);
    expect(commands[1]).toEqual(['config', 'G-ABC123', { send_page_view: false }]);
    expect(window.fbq?.queue).toEqual([['init', '1234567890']]);
  });

  it('given a page view, when sent, then the address has no query string', () => {
    loadAnalytics({ ga4: 'G-ABC123', pixel: '1234567890' });

    trackPageView('/buscar', 'Resultados');

    const last = [...(window.dataLayer?.at(-1) as IArguments)] as unknown[];
    expect(last).toEqual([
      'event',
      'page_view',
      { page_location: `${window.location.origin}/buscar`, page_path: '/buscar', page_title: 'Resultados' },
    ]);
    expect(window.fbq?.queue.at(-1)).toEqual(['track', 'PageView']);
  });

  it('given only GA4, when loaded, then the Pixel never loads', () => {
    loadAnalytics({ ga4: 'G-ABC123' });

    expect(scripts()).toEqual(['https://www.googletagmanager.com/gtag/js?id=G-ABC123']);
    expect(window.fbq).toBeUndefined();
  });

  it('given analytics cookies, when consent is revoked, then they are deleted and others stay', () => {
    document.cookie = '_ga=GA1.1.1; path=/';
    document.cookie = '_ga_ABC123=GS1.1; path=/';
    document.cookie = '_fbp=fb.1.1; path=/';
    document.cookie = 'XSRF-TOKEN=keep; path=/';

    clearAnalyticsCookies();

    expect(document.cookie).toBe('XSRF-TOKEN=keep');
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });
});
