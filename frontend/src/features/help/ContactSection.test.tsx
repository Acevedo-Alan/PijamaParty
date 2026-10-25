import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { listed, product, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { clearStoreContact, setStoreContact } from '../../test/storeContact.ts';

const messages = es.contact;

const contact = () => screen.queryByRole('region', { name: messages.title });

describe('"Contactanos" of the home page (UC-30, D-09)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    clearStoreContact();
  });

  it('given the email and the WhatsApp number, when the home page opens, then it links both after the grid, with no form', async () => {
    setStoreContact({ email: 'hola@example.com', whatsAppNumber: '5493424000000' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    const section = contact() as HTMLElement;
    expect(within(section).getByRole('link', { name: 'hola@example.com' })).toHaveAttribute('href', 'mailto:hola@example.com');
    const whatsApp = within(section).getByRole('link', { name: messages.whatsApp });
    expect(whatsApp).toHaveAttribute('href', 'https://wa.me/5493424000000');
    expect(whatsApp).toHaveAttribute('target', '_blank');
    expect(whatsApp).toHaveAttribute('rel', 'noopener noreferrer');
    expect(within(section).queryByRole('textbox')).not.toBeInTheDocument();
    const grid = screen.getByRole('region', { name: es.catalog.allTitle });
    expect(grid.compareDocumentPosition(section) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('given only the email (D-15), when the home page opens, then there is no WhatsApp link', async () => {
    setStoreContact({ email: 'hola@example.com' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(within(contact() as HTMLElement).getByRole('link', { name: 'hola@example.com' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: messages.whatsApp })).not.toBeInTheDocument();
  });

  it('given neither the email nor the WhatsApp number yet (D-15), when the home page opens, then there is no section', async () => {
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(contact()).not.toBeInTheDocument();
  });

  it('given a category page, when it opens, then it shows no contact section', async () => {
    setStoreContact({ email: 'hola@example.com' });
    stubCatalogApi({ listings: [listed([product('rosa')])] });

    renderRoute('/categoria/verano');

    await screen.findByRole('link', { name: /Pijama rosa/ });
    expect(contact()).not.toBeInTheDocument();
  });
});
