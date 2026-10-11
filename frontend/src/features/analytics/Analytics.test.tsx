import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { signedOut, stubApi } from '../../test/adminApi.ts';
import { listed, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { resetAnalyticsForTests } from './analytics.ts';
import { pageReload } from './Analytics.tsx';
import { saveConsent } from './consent.ts';

const consent = es.consent;

function scripts(): string[] {
  return [...document.head.querySelectorAll('script')].map((script) => script.src);
}

function banner() {
  return screen.queryByRole('region', { name: consent.title });
}

describe('analytics consent (RNF-25, T-E1-23)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_GA4_MEASUREMENT_ID', 'G-ABC123');
    vi.stubEnv('VITE_META_PIXEL_ID', '1234567890');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
    resetAnalyticsForTests();
    localStorage.clear();
    document.head.querySelectorAll('script').forEach((script) => {
      script.remove();
    });
  });

  it('given a first visit, when the shop opens, then the banner asks and nothing loads before accepting', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/');

    const region = await screen.findByRole('region', { name: consent.title });
    expect(within(region).getByText(consent.text)).toBeInTheDocument();
    expect(scripts()).toEqual([]);
    expect(window.dataLayer).toBeUndefined();
  });

  it('given the banner, when accepted, then both tools load, the choice is kept and the page view has no query', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/buscar?q=pijama');

    await userEvent.click(await screen.findByRole('button', { name: consent.accept }));

    expect(banner()).not.toBeInTheDocument();
    expect(scripts()).toHaveLength(2);
    expect(JSON.parse(localStorage.getItem('pp-consent') ?? '{}')).toMatchObject({ choice: 'accepted' });
    await waitFor(() => {
      const events = (window.dataLayer ?? []).map((entry) => [...(entry as IArguments)] as unknown[]);
      expect(events).toContainEqual([
        'event',
        'page_view',
        expect.objectContaining({ page_location: `${window.location.origin}/buscar`, page_path: '/buscar' }),
      ]);
    });
  });

  it('given the banner, when rejected, then nothing loads and the banner does not come back', async () => {
    stubCatalogApi({ listings: [listed([])] });
    const first = renderRoute('/');
    await userEvent.click(await screen.findByRole('button', { name: consent.reject }));
    expect(banner()).not.toBeInTheDocument();
    first.unmount();

    renderRoute('/');

    await screen.findByRole('heading', { level: 1 });
    expect(banner()).not.toBeInTheDocument();
    expect(scripts()).toEqual([]);
  });

  it('given an accepted choice, when changed to rejected from the footer, then the cookies go and the page reloads', async () => {
    saveConsent('accepted');
    document.cookie = '_ga=GA1.1.1; path=/';
    const reload = vi.spyOn(pageReload, 'run').mockImplementation(() => undefined);
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/');
    await waitFor(() => {
      expect(scripts()).toHaveLength(2);
    });
    expect(banner()).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: consent.footerLink }));
    expect(within(banner() as HTMLElement).getByText(consent.current.accepted)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: consent.reject }));

    expect(document.cookie).not.toContain('_ga=');
    expect(reload).toHaveBeenCalledOnce();
  });

  it('given no analytics ids in the build, when the shop opens, then there is no banner nor Cookies link', async () => {
    vi.stubEnv('VITE_GA4_MEASUREMENT_ID', '');
    vi.stubEnv('VITE_META_PIXEL_ID', '');
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/');

    await screen.findByRole('heading', { level: 1 });
    expect(banner()).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: consent.footerLink })).not.toBeInTheDocument();
  });

  it('given the back office, when it opens, then it never asks nor loads analytics', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedOut });
    renderRoute('/admin/ingresar');

    await screen.findByRole('heading', { level: 1 });
    expect(banner()).not.toBeInTheDocument();
    expect(scripts()).toEqual([]);
  });
});
