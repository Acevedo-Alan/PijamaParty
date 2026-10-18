import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { answer, NO_CART, NO_REVIEWS, productDetail, REVIEWS_PATH, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = es.reviews.product;

function review(authorName: string, rating: number, text: string | null, createdAt = '2026-10-18T15:00:00Z') {
  return { authorName, rating, text, createdAt };
}

function reviews(average: number | null, count: number, items: unknown[]) {
  return answer(200, { average, count, reviews: items });
}

/** The product page of a visitor who is not signed in; the reviews answer in order, the last one repeats. */
function stubPage(...answers: unknown[]) {
  const queue = [...answers];
  return vi.spyOn(api, 'GET').mockImplementation(withHeader((path: string) => {
    if (path === '/api/cart') {
      return Promise.resolve(NO_CART);
    }
    if (path === REVIEWS_PATH) {
      return Promise.resolve(queue.length > 1 ? queue.shift() : queue[0]);
    }
    return Promise.resolve(answer(200, productDetail()));
  }));
}

async function reviewsSection() {
  return within(await screen.findByRole('region', { name: messages.title }));
}

describe('the approved reviews on the product page (UC-24)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given approved reviews of 4 and 5, when the product opens, then the average is 4,5 with the count and the reviews show', async () => {
    const get = stubPage(reviews(4.5, 2, [
      review('Bruno', 5, null, '2026-10-18T15:00:00Z'),
      review('Ana', 4, 'Muy cómodo\npara dormir', '2026-10-11T15:00:00Z'),
    ]));

    renderRoute('/producto/luna');

    const summary = await screen.findByRole('link', { name: new RegExp(messages.summary('4,5', 2)) });
    expect(summary).toHaveAttribute('href', '#opiniones');
    expect(within(summary).getByRole('img', { name: messages.stars('4,5') })).toBeInTheDocument();
    const section = await reviewsSection();
    const items = await section.findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0] as HTMLElement).getByRole('img', { name: messages.rating(5) })).toBeInTheDocument();
    expect(items[0]).toHaveTextContent('Bruno · 18/10/26');
    expect(items[1]).toHaveTextContent('Ana · 11/10/26');
    expect(within(items[1] as HTMLElement).getByText(/Muy cómodo/)).toBeInTheDocument();
    expect(section.queryByRole('button', { name: messages.more })).not.toBeInTheDocument();
    expect(get).toHaveBeenCalledWith(REVIEWS_PATH, expect.objectContaining({
      params: { path: { slug: 'luna' }, query: { page: 0 } },
    }));
  });

  it('given a product without approved reviews, when it opens, then no average shows and the section says so', async () => {
    stubPage(NO_REVIEWS);

    renderRoute('/producto/luna');

    expect(await (await reviewsSection()).findByText(messages.empty)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /opini/ })).not.toBeInTheDocument();
  });

  it('given more reviews than a page, when the visitor asks for more, then the older ones are added until there are no more', async () => {
    const firstPage = Array.from({ length: 10 }, (_, index) => review(`Cliente${String(index)}`, 5, null));
    const get = stubPage(reviews(5, 11, firstPage), reviews(5, 11, [review('Última', 4, 'Lindo')]));
    renderRoute('/producto/luna');
    const section = await reviewsSection();
    expect(await section.findAllByRole('listitem')).toHaveLength(10);

    await userEvent.click(section.getByRole('button', { name: messages.more }));

    expect(await section.findByText(/Última/)).toBeInTheDocument();
    expect(section.getAllByRole('listitem')).toHaveLength(11);
    expect(section.queryByRole('button', { name: messages.more })).not.toBeInTheDocument();
    expect(get).toHaveBeenCalledWith(REVIEWS_PATH, expect.objectContaining({
      params: { path: { slug: 'luna' }, query: { page: 1 } },
    }));
  });

  it('given the reviews fail to load, when the visitor retries, then they show', async () => {
    stubPage(answer(500), reviews(4, 1, [review('Ana', 4, null)]));
    renderRoute('/producto/luna');
    const section = await reviewsSection();

    await userEvent.click(await section.findByRole('button', { name: messages.retry }));

    expect(await section.findByText(/Ana/)).toBeInTheDocument();
  });
});
