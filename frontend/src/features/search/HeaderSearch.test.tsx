import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { answer, listed, product, queriesTo, stubCatalogApi, suggested } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const SUGGESTIONS = '/api/catalog/suggestions';

function field() {
  return screen.getByRole('combobox', { name: es.search.label });
}

describe('header search (UC-26)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given 2 characters typed, when the visitor pauses, then up to 5 suggestions with photo and name appear', async () => {
    const get = stubCatalogApi({ listings: [listed([])], suggestions: suggested([product('luna'), product('sol')]) });
    renderRoute('/');

    await userEvent.type(field(), 'p');
    expect(queriesTo(get, SUGGESTIONS)).toEqual([]);
    await userEvent.type(field(), 'i');

    const list = await screen.findByRole('listbox', { name: es.search.suggestions });
    expect(within(list).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Pijama luna',
      'Pijama sol',
    ]);
    expect(within(list).getAllByTestId('blurred-image')).toHaveLength(2);
    expect(field()).toHaveAttribute('aria-expanded', 'true');
    expect(queriesTo(get, SUGGESTIONS)).toEqual([{ q: 'pi' }]);
  });

  it('given suggestions, when one is clicked, then its product opens', async () => {
    stubCatalogApi({ listings: [listed([])], suggestions: suggested([product('luna')]) });
    const { router } = renderRoute('/');

    await userEvent.type(field(), 'luna');
    await userEvent.click(await screen.findByRole('option', { name: 'Pijama luna' }));

    expect(router.state.location.pathname).toBe('/producto/luna');
  });

  it('given suggestions, when the arrows highlight one and Enter is pressed, then that product opens', async () => {
    stubCatalogApi({ listings: [listed([])], suggestions: suggested([product('luna'), product('sol')]) });
    const { router } = renderRoute('/');

    await userEvent.type(field(), 'pijama');
    await screen.findByRole('listbox');
    await userEvent.keyboard('{ArrowDown}{ArrowDown}');

    expect(screen.getByRole('option', { name: 'Pijama sol' })).toHaveAttribute('aria-selected', 'true');
    await userEvent.keyboard('{Enter}');
    expect(router.state.location.pathname).toBe('/producto/sol');
  });

  it('given a text, when Enter is pressed without a highlighted suggestion, then the results page opens', async () => {
    stubCatalogApi({ listings: [listed([])] });
    const { router } = renderRoute('/categoria/verano?talle=M');

    await userEvent.type(field(), '  pijama de algodón {Enter}');

    expect(router.state.location.pathname).toBe('/buscar');
    expect(new URLSearchParams(router.state.location.search).get('q')).toBe('pijama de algodón');
    expect(field()).toHaveValue('pijama de algodón');
  });

  it('given a single character, when Enter is pressed, then nothing happens', async () => {
    const get = stubCatalogApi({ listings: [listed([])] });
    const { router } = renderRoute('/');

    await userEvent.type(field(), 'p{Enter}');

    expect(router.state.location.pathname).toBe('/');
    await new Promise((resolve) => setTimeout(resolve, 300));
    expect(queriesTo(get, SUGGESTIONS)).toEqual([]);
  });

  it('given a failed suggestion request, when typing, then the field keeps working without suggestions', async () => {
    stubCatalogApi({ listings: [listed([])], suggestions: answer(500) });
    const { router } = renderRoute('/');

    await userEvent.type(field(), 'pijama');
    await new Promise((resolve) => setTimeout(resolve, 300));
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    await userEvent.keyboard('{Enter}');
    expect(router.state.location.pathname).toBe('/buscar');
  });

  it('given open suggestions, when Escape is pressed, then the list closes', async () => {
    stubCatalogApi({ listings: [listed([])], suggestions: suggested([product('luna')]) });
    renderRoute('/');

    await userEvent.type(field(), 'luna');
    await screen.findByRole('listbox');
    await userEvent.keyboard('{Escape}');

    expect(field()).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('given a phone, when the search icon is used, then a full-screen search opens and closes on navigating', async () => {
    stubCatalogApi({ listings: [listed([])], suggestions: suggested([product('luna')]) });
    const { router } = renderRoute('/');

    await userEvent.click(screen.getByLabelText(es.search.open, { selector: 'button' }));
    const screenDialog = screen.getByRole('dialog', { name: es.search.title });
    const fullScreenField = within(screenDialog).getByRole('combobox', { name: es.search.label });
    expect(fullScreenField).toHaveFocus();

    await userEvent.type(fullScreenField, 'luna');
    await userEvent.click(await within(screenDialog).findByRole('option', { name: 'Pijama luna' }));

    expect(router.state.location.pathname).toBe('/producto/luna');
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  it('given the full-screen search, when closed, then it goes away', async () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/');

    await userEvent.click(screen.getByLabelText(es.search.open, { selector: 'button' }));
    await userEvent.click(screen.getByRole('button', { name: es.search.close }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
