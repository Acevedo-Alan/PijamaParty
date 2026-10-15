import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { answer, listed, stubCatalogApi } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const CODE = 'ARR-7K2M9QXZ';

function stubPost(response: unknown) {
  return vi.spyOn(api, 'POST').mockResolvedValue(response);
}

async function sendForm(orderNumber = '#1001', email = 'ana@example.com') {
  await userEvent.type(screen.getByLabelText(es.withdrawal.orderNumber), orderNumber);
  await userEvent.type(screen.getByLabelText(es.withdrawal.email), email);
  await userEvent.click(screen.getByRole('button', { name: es.withdrawal.send }));
}

describe('withdrawal button (UC-29, T-E3-25)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a paid order, when the form is sent with its number and email, then the procedure code is shown', async () => {
    stubCatalogApi({ listings: [listed([])] });
    const post = stubPost(answer(201, { code: CODE }));
    renderRoute('/arrepentimiento');

    await sendForm(' #1001 ', ' Ana@Example.com ');

    const done = await screen.findByRole('status');
    expect(done).toHaveTextContent(es.withdrawal.requested);
    expect(within(done).getByText(CODE)).toBeInTheDocument();
    expect(done).toHaveTextContent(es.withdrawal.keepIt);
    expect(done).toHaveFocus();
    expect(post).toHaveBeenCalledWith('/api/withdrawals', { body: { orderNumber: '#1001', email: 'Ana@Example.com' } });
    expect(screen.queryByRole('button', { name: es.withdrawal.send })).not.toBeInTheDocument();
  });

  it('given an order with an active request, when another is sent, then the same code is shown as already requested', async () => {
    stubCatalogApi({ listings: [listed([])] });
    stubPost(answer(200, { code: CODE }));
    renderRoute('/arrepentimiento');

    await sendForm();

    const done = await screen.findByRole('status');
    expect(done).toHaveTextContent(es.withdrawal.alreadyRequested);
    expect(within(done).getByText(CODE)).toBeInTheDocument();
  });

  it.each([
    [422, 'NO_MATCHING_ORDER', es.withdrawal.noMatchingOrder],
    [409, 'ORDER_NOT_WITHDRAWABLE', es.withdrawal.notWithdrawable],
    [429, undefined, es.withdrawal.tooManyAttempts],
  ])('given a %i answer, when the form is sent, then the form stays and says why', async (status, problem, message) => {
    stubCatalogApi({ listings: [listed([])] });
    stubPost(answer(status, problem === undefined ? undefined : { problem }));
    renderRoute('/arrepentimiento');

    await sendForm();

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('button', { name: es.withdrawal.send })).toBeEnabled();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('given an empty field, when the form is shown, then it cannot be sent', async () => {
    stubCatalogApi({ listings: [listed([])] });
    const post = stubPost(answer(201, { code: CODE }));
    renderRoute('/arrepentimiento');

    await userEvent.type(screen.getByLabelText(es.withdrawal.orderNumber), '1001');

    expect(screen.getByRole('button', { name: es.withdrawal.send })).toBeDisabled();
    expect(post).not.toHaveBeenCalled();
  });

  it('given any public page, when it opens, then its footer links to the withdrawal button', async () => {
    stubCatalogApi({ listings: [listed([])] });
    const { router } = renderRoute('/categoria/pijamas');

    const footer = screen.getByRole('contentinfo');
    await userEvent.click(within(footer).getByRole('link', { name: es.withdrawal.link }));

    expect(router.state.location.pathname).toBe('/arrepentimiento');
    expect(await screen.findByRole('heading', { name: es.withdrawal.title })).toBeInTheDocument();
  });

  it('given the home page, when it opens, then it shows a visible block with the withdrawal button', () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/');

    const block = screen.getByRole('region', { name: es.withdrawal.link });
    expect(within(block).getByRole('link', { name: es.withdrawal.link })).toHaveAttribute('href', '/arrepentimiento');
  });

  it('given a category page, when it opens, then the home block is not there', () => {
    stubCatalogApi({ listings: [listed([])] });
    renderRoute('/categoria/pijamas');

    expect(screen.queryByRole('region', { name: es.withdrawal.link })).not.toBeInTheDocument();
  });
});
