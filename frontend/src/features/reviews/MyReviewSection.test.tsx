import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { answer, detailOrNoReviews, NO_CART, productDetail, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = es.reviews.mine;
const PRODUCT_ID = productDetail().id ?? '';
const ANA = answer(200, { id: 'customer-1', email: 'ana@example.com', name: 'Ana Pérez', phone: null });

/** The product page with Ana signed in (or nobody), answering her standing on the product. */
function stubPage(standing: unknown, account: unknown = ANA) {
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) => {
    if (path === '/api/cart') {
      return Promise.resolve(NO_CART);
    }
    if (path === '/api/account/reviews/{productId}') {
      return Promise.resolve(standing);
    }
    return Promise.resolve(detailOrNoReviews(path));
  }, account));
}

function review(rating: number, text: string | null, status: string) {
  return { rating, text, status, updatedAt: '2026-10-18T12:00:00Z' };
}

describe('the customer leaves a review (UC-24)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a visitor who is not signed in, when the product opens, then it is invited to sign in', async () => {
    stubPage(undefined, answer(401));

    renderRoute('/producto/luna');

    expect(await screen.findByRole('link', { name: messages.signIn })).toHaveAttribute('href', '/cuenta/ingresar');
    expect(screen.queryByRole('group', { name: messages.rating })).not.toBeInTheDocument();
  });

  it('given a customer who did not buy the product, when it opens, then it is told only buyers can review it', async () => {
    stubPage(answer(200, { eligible: false, review: null }));

    renderRoute('/producto/luna');

    expect(await screen.findByText(messages.notABuyer)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: messages.send })).not.toBeInTheDocument();
  });

  it('given a buyer, when it rates the product and sends it, then the review waits for moderation', async () => {
    const get = stubPage(answer(200, { eligible: true, review: null }));
    const put = vi.spyOn(api, 'PUT').mockResolvedValue(answer(201, review(4, 'Muy cómodo', 'PENDING')));
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('radio', { name: messages.stars(4) }));
    await userEvent.type(screen.getByLabelText(messages.text), 'Muy cómodo');
    await userEvent.click(screen.getByRole('button', { name: messages.send }));

    expect(put).toHaveBeenCalledWith('/api/account/reviews/{productId}', {
      params: { path: { productId: PRODUCT_ID } },
      body: { rating: 4, text: 'Muy cómodo' },
    });
    expect(await screen.findByText(messages.sent)).toBeInTheDocument();
    expect(screen.getByText(messages.status.PENDING)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: messages.save })).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/account/reviews/{productId}', expect.objectContaining({
      params: { path: { productId: PRODUCT_ID } },
    }));
  });

  it('given an approved review, when its author edits it, then it goes back to moderation', async () => {
    stubPage(answer(200, { eligible: true, review: review(5, 'Muy cómodo', 'APPROVED') }));
    const put = vi.spyOn(api, 'PUT').mockResolvedValue(answer(200, review(3, 'Muy cómodo', 'PENDING')));
    renderRoute('/producto/luna');

    expect(await screen.findByText(messages.status.APPROVED)).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: messages.stars(5) })).toBeChecked();
    expect(screen.getByLabelText(messages.text)).toHaveValue('Muy cómodo');
    await userEvent.click(screen.getByRole('radio', { name: messages.stars(3) }));
    await userEvent.click(screen.getByRole('button', { name: messages.save }));

    expect(put).toHaveBeenCalledWith('/api/account/reviews/{productId}', expect.objectContaining({
      body: { rating: 3, text: 'Muy cómodo' },
    }));
    expect(await screen.findByText(messages.status.PENDING)).toBeInTheDocument();
  });

  it('given a rejected review of a cancelled order, when its author opens the product, then it sees it but cannot edit it', async () => {
    stubPage(answer(200, { eligible: false, review: review(2, 'Destiñe', 'REJECTED') }));

    renderRoute('/producto/luna');

    expect(await screen.findByText(messages.status.REJECTED)).toBeInTheDocument();
    expect(screen.getByText('Destiñe')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: messages.save })).not.toBeInTheDocument();
  });

  it('given no rating picked, when sent, then it asks for one without calling the server', async () => {
    stubPage(answer(200, { eligible: true, review: null }));
    const put = vi.spyOn(api, 'PUT');
    renderRoute('/producto/luna');

    await userEvent.click(await screen.findByRole('button', { name: messages.send }));

    expect(screen.getByText(messages.pickRating)).toBeInTheDocument();
    expect(screen.getByRole('group', { name: messages.rating })).toHaveAccessibleDescription(messages.pickRating);
    expect(put).not.toHaveBeenCalled();
  });

  it('given the server refuses the text or the buyer, when sent, then it says why', async () => {
    stubPage(answer(200, { eligible: true, review: null }));
    vi.spyOn(api, 'PUT')
      .mockResolvedValueOnce(answer(422, { problem: 'INVALID_REVIEW', fields: ['text'] }))
      .mockResolvedValueOnce(answer(422, { problem: 'NOT_A_BUYER' }));
    renderRoute('/producto/luna');
    await userEvent.click(await screen.findByRole('radio', { name: messages.stars(5) }));

    await userEvent.click(screen.getByRole('button', { name: messages.send }));
    expect(await screen.findByText(messages.textTooLong(1000))).toBeInTheDocument();
    expect(screen.getByLabelText(messages.text)).toHaveAttribute('aria-invalid', 'true');

    await userEvent.click(screen.getByRole('button', { name: messages.send }));
    expect(await screen.findByText(messages.notABuyer)).toBeInTheDocument();
  });
});
