import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { withRoot } from '../../routes.tsx';
import { signedIn, stubApi } from '../../test/adminApi.ts';
import { listed, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { setStoreContact } from '../../test/storeContact.ts';

const EMAIL = 'hola@example.com';
const WHATSAPP = '5493424000000';

const whatsAppLink = () => screen.queryByRole('link', { name: es.contact.whatsAppButtonLabel });
const emailLink = () => screen.queryByRole('link', { name: EMAIL });

function renderFailingPage() {
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
  const routes = withRoot([{ path: '/roto', lazy: () => Promise.reject(new Error('render failed')) }]);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/roto'] })} />
    </QueryClientProvider>,
  );
}

describe('the 404 and 500 pages lead to the store and to the contact (RNF-31.7, T-E6-25)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the store email and WhatsApp, when an unknown path opens, then the 404 links the store, WhatsApp in a new tab and the email', async () => {
    setStoreContact({ email: EMAIL, whatsAppNumber: WHATSAPP });
    stubCatalogApi({ listings: [listed([])] });

    renderRoute('/no-existe');

    // The footer and the floating button link the contact too: only the page's own links count here.
    const main = within(await screen.findByRole('main'));
    expect(await main.findByRole('heading', { level: 1, name: es.notFound.title })).toBeInTheDocument();
    expect(main.getByRole('link', { name: es.notFound.backToStore })).toHaveAttribute('href', '/');
    const whatsApp = await main.findByRole('link', { name: es.contact.whatsAppButtonLabel });
    expect(whatsApp).toHaveAttribute('href', `https://wa.me/${WHATSAPP}`);
    expect(whatsApp).toHaveAttribute('target', '_blank');
    expect(whatsApp).toHaveAttribute('rel', 'noopener noreferrer');
    expect(main.getByRole('link', { name: EMAIL })).toHaveAttribute('href', `mailto:${EMAIL}`);
    expect(screen.getByRole('main')).toHaveTextContent(`${es.errorPages.needHelp} ${es.contact.whatsAppButton} ${es.errorPages.orTo} ${EMAIL}`);
  });

  it('given only the store email, when the error page shows, then it links the store and the email, with no WhatsApp', async () => {
    setStoreContact({ email: EMAIL });

    renderFailingPage();

    expect(await screen.findByRole('heading', { level: 1, name: es.routeError.title })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: es.notFound.backToStore })).toHaveAttribute('href', '/');
    expect(await screen.findByRole('link', { name: EMAIL })).toHaveAttribute('href', `mailto:${EMAIL}`);
    expect(screen.getByRole('main')).toHaveTextContent(`${es.errorPages.needHelp} ${es.errorPages.writeTo} ${EMAIL}`);
    expect(whatsAppLink()).not.toBeInTheDocument();
  });

  it('given only the store WhatsApp, when the error page shows, then it links WhatsApp, with no email', async () => {
    setStoreContact({ whatsAppNumber: WHATSAPP });

    renderFailingPage();

    expect(await screen.findByRole('link', { name: es.contact.whatsAppButtonLabel })).toHaveAttribute(
      'href',
      `https://wa.me/${WHATSAPP}`,
    );
    expect(emailLink()).not.toBeInTheDocument();
  });

  it('given no contact data yet, when the error page shows, then there is no contact line', async () => {
    renderFailingPage();

    expect(await screen.findByRole('heading', { level: 1, name: es.routeError.title })).toBeInTheDocument();
    expect(screen.queryByText(es.errorPages.needHelp, { exact: false })).not.toBeInTheDocument();
  });

  it('given the back office, when an unknown path opens, then its 404 leaves out the store contact', async () => {
    setStoreContact({ email: EMAIL, whatsAppNumber: WHATSAPP });
    stubApi({ 'GET /api/admin/auth/me': signedIn });

    renderRoute('/admin/no-existe');

    expect(await screen.findByRole('heading', { level: 1, name: es.notFound.title })).toBeInTheDocument();
    expect(screen.queryByText(es.errorPages.needHelp, { exact: false })).not.toBeInTheDocument();
  });
});
