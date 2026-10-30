import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { components } from '../../api/schema.d.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { problem, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

type Row = components['schemas']['ModeratedReviewResponse'];

const messages = esAdmin.reviewList;
const LIST = 'GET /api/admin/reviews';
const APPROVE = 'POST /api/admin/reviews/{id}/approve';
const REJECT = 'POST /api/admin/reviews/{id}/reject';
const DELETE = 'DELETE /api/admin/reviews/{id}';

function row(authorName: string, overrides: Partial<Row> = {}): Row {
  return {
    id: `r-${authorName}`,
    product: { id: 'p-1', name: 'Pijama Luna', slug: 'pijama-luna' },
    authorName,
    rating: 4,
    text: 'Muy cómodo',
    status: 'PENDING',
    updatedAt: '2026-10-13T18:00:00Z',
    ...overrides,
  };
}

function card(authorName: string) {
  return within(screen.getByText(new RegExp(`^${authorName} · enviada`)).closest('li') as HTMLElement);
}

describe('review moderation in the back office (UC-25)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given pending reviews, when the panel opens, then it shows them as the server sends them, oldest first', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: [
        answer(200, {
          items: [row('Ana', { rating: 5 }), row('Bruno', { text: null })],
          hasMore: true,
        }),
        answer(200, {
          items: [row('Carla', { product: { id: 'p-2', name: null, slug: null } })],
          hasMore: false,
        }),
      ],
    });
    renderRoute('/admin/resenas');

    await screen.findByText(/^Ana · enviada el/);
    expect(screen.getByRole('button', { name: messages.statuses.PENDING })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getAllByText(/ · enviada el /).map((meta) => meta.textContent.split(' · ')[0])).toEqual([
      'Ana',
      'Bruno',
    ]);
    const ana = card('Ana');
    expect(ana.getByRole('link', { name: 'Pijama Luna' })).toHaveAttribute('href', '/producto/pijama-luna');
    expect(ana.getByRole('img', { name: messages.rating(5) })).toBeInTheDocument();
    expect(ana.getByText('Muy cómodo')).toBeInTheDocument();
    expect(ana.getByRole('button', { name: messages.approve })).toBeEnabled();
    expect(ana.getByRole('button', { name: messages.reject })).toBeEnabled();
    expect(card('Bruno').getByText(messages.noText)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: esAdmin.showMore }));

    await screen.findByText(/^Carla · enviada el/);
    expect(card('Carla').getByText(messages.productGone)).toBeInTheDocument();
    expect(api.callsTo(LIST).map((call) => call.options.params?.query)).toEqual([
      { status: 'PENDING', page: 0 },
      { status: 'PENDING', page: 1 },
    ]);
  });

  it('given the panel, when the rejected tab is chosen, then only those are asked for and the tab is in the address', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: (options: { params?: { query?: { status?: string } } }) =>
        options.params?.query?.status === 'REJECTED'
          ? answer(200, {
              items: [row('Bruno', { status: 'REJECTED' })],
              hasMore: false,
            })
          : answer(200, { items: [row('Ana')], hasMore: false }),
    });
    const { router } = renderRoute('/admin/resenas');
    await screen.findByText(/^Ana · enviada el/);

    await userEvent.click(screen.getByRole('button', { name: messages.statuses.REJECTED }));

    await screen.findByText(/^Bruno · enviada el/);
    expect(api.callsTo(LIST).at(-1)?.options.params?.query).toEqual({
      status: 'REJECTED',
      page: 0,
    });
    expect(router.state.location.search).toBe('?estado=REJECTED');
    const bruno = card('Bruno');
    expect(bruno.getByRole('button', { name: messages.approve })).toBeInTheDocument();
    expect(bruno.queryByRole('button', { name: messages.reject })).not.toBeInTheDocument();
  });

  it('given no reviews in a tab, when it opens, then it says so', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, { items: [], hasMore: false }),
    });
    renderRoute('/admin/resenas?estado=APPROVED');

    expect(await screen.findByText(messages.empty.APPROVED)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: messages.statuses.APPROVED })).toHaveAttribute('aria-pressed', 'true');
  });

  it('given a pending review, when approved, then it is sent, confirmed and the list is read again', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: [answer(200, { items: [row('Ana')], hasMore: false }), answer(200, { items: [], hasMore: false })],
      [APPROVE]: answer(200, row('Ana', { status: 'APPROVED' })),
    });
    renderRoute('/admin/resenas');
    await screen.findByText(/^Ana · enviada el/);

    await userEvent.click(card('Ana').getByRole('button', { name: messages.approve }));

    expect(await screen.findByRole('status')).toHaveTextContent(messages.approved('Ana'));
    expect(api.callsTo(APPROVE)[0]?.options.params?.path).toEqual({
      id: 'r-Ana',
    });
    expect(await screen.findByText(messages.empty.PENDING)).toBeInTheDocument();
  });

  it('given a review someone deleted, when rejected, then it says so and reads the list again', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, { items: [row('Ana')], hasMore: false }),
      [REJECT]: problem(404),
    });
    renderRoute('/admin/resenas');
    await screen.findByText(/^Ana · enviada el/);

    await userEvent.click(card('Ana').getByRole('button', { name: messages.reject }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.notFound);
    await vi.waitFor(() => {
      expect(api.callsTo(LIST)).toHaveLength(2);
    });
  });

  it('given a review, when deleted after confirming, then it is sent and confirmed', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, { items: [row('Ana')], hasMore: false }),
      [DELETE]: answer(204),
    });
    renderRoute('/admin/resenas');
    await screen.findByText(/^Ana · enviada el/);

    await userEvent.click(card('Ana').getByRole('button', { name: messages.delete }));

    expect(confirm).toHaveBeenCalledWith(messages.confirmDelete('Ana'));
    expect(await screen.findByRole('status')).toHaveTextContent(messages.deleted('Ana'));
    expect(api.callsTo(DELETE)[0]?.options.params?.path).toEqual({
      id: 'r-Ana',
    });
  });

  it('given a confirmation refused, when deleting, then nothing is sent', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const api = stubApi({
      'GET /api/admin/auth/me': signedIn,
      [LIST]: answer(200, { items: [row('Ana')], hasMore: false }),
    });
    renderRoute('/admin/resenas');
    await screen.findByText(/^Ana · enviada el/);

    await userEvent.click(card('Ana').getByRole('button', { name: messages.delete }));

    expect(api.callsTo(DELETE)).toHaveLength(0);
  });
});
