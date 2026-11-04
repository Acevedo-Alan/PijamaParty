import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { listed, product, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { clearStoreContact, setStoreContact } from '../../test/storeContact.ts';

const NAME = es.contact.whatsAppButton;

const button = () => screen.queryByRole('link', { name: NAME });

const shownButton = () => screen.findByRole('link', { name: NAME });

describe('floating WhatsApp button (UC-31, T-E7-27)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    clearStoreContact();
  });

  it.each(['/', '/producto/pijama-rosa', '/preguntas-frecuentes'])(
    'given the WhatsApp number, when %s opens, then the button opens the chat in a new tab',
    async (path) => {
      setStoreContact({ whatsAppNumber: '5493424000000' });
      stubCatalogApi({ listings: [listed([product('rosa')])] });

      renderRoute(path);

      const link = await shownButton();
      expect(link).toHaveAttribute('href', 'https://wa.me/5493424000000');
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', 'noopener noreferrer');
      expect(link.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    },
  );

  it.each(['/checkout', '/cuenta/pedidos'])('given the WhatsApp number, when %s opens, then there is no button', async (path) => {
    setStoreContact({ whatsAppNumber: '5493424000000' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute(path);

    // Once the footer shows the number, the contact data has arrived.
    expect(await within(screen.getByRole('contentinfo')).findByRole('link', { name: '+54 9 3424000000' })).toBeInTheDocument();
    expect(button()).not.toBeInTheDocument();
  });

  it('given no WhatsApp number yet (D-15), when the home page opens, then there is no button', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(button()).not.toBeInTheDocument();
  });
});
