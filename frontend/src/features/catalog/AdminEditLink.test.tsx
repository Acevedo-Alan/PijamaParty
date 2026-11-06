import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { answer } from '../../test/catalogApi.ts';
import { maybeAdmin, markAdmin } from '../admin/adminHint.ts';
import { AdminEditLink } from './AdminEditLink.tsx';

const PRODUCT = '0d6f8f64-2c4e-4b1f-9a55-6f3a1d2c7b10';

function renderLink() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AdminEditLink productId={PRODUCT} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('the administrator shortcut on the product page (T-E8-27, UC-03)', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a browser that never opened the back office, when the page renders, then it asks nothing and shows nothing', () => {
    const get = vi.spyOn(api, 'GET');

    renderLink();

    expect(get).not.toHaveBeenCalled();
    expect(screen.queryByRole('link', { name: es.product.adminEdit })).not.toBeInTheDocument();
  });

  it('given a signed-in administrator, when the page renders, then it links to the back-office form of the product', async () => {
    markAdmin();
    vi.spyOn(api, 'GET').mockResolvedValue(answer(200, { id: 'a1', name: 'Alan' }));

    renderLink();

    expect(await screen.findByRole('link', { name: es.product.adminEdit })).toHaveAttribute(
      'href',
      `/admin/productos/${PRODUCT}`,
    );
  });

  it('given a browser with the hint but no session, when the page renders, then nothing shows and the hint goes', async () => {
    markAdmin();
    vi.spyOn(api, 'GET').mockResolvedValue(answer(401, undefined));

    renderLink();

    await waitFor(() => {
      expect(maybeAdmin()).toBe(false);
    });
    expect(screen.queryByRole('link', { name: es.product.adminEdit })).not.toBeInTheDocument();
  });
});
