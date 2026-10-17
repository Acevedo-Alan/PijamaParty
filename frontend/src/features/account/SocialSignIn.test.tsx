import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { answer, NO_CART, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const TOKEN = 'A'.repeat(42) + '1';
const messages = es.account.social;

/** The storefront's GET calls: the enabled providers, and an empty cart for everything else. */
function stubStore(providers: string[] = ['GOOGLE', 'FACEBOOK']) {
  return vi.spyOn(api, 'GET').mockImplementation(
    withHeader((path) =>
      Promise.resolve(path === '/api/account/oauth2/providers' ? answer(200, { providers }) : NO_CART),
    ),
  );
}

function stubPost(...answers: unknown[]) {
  const queue = [...answers];
  return vi.spyOn(api, 'POST').mockImplementation((() =>
    Promise.resolve(queue.length > 1 ? queue.shift() : queue[0])) as never);
}

describe('sign-in and registration with Google and Facebook (UC-27, T-E4-14)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given both providers enabled, when the sign-in page opens, then each button starts its provider', async () => {
    stubStore();
    renderRoute('/cuenta/ingresar');

    const google = await screen.findByRole('link', { name: messages.continueWith('Google') });
    expect(google).toHaveAttribute('href', '/api/account/oauth2/authorization/google');
    expect(screen.getByRole('link', { name: messages.continueWith('Facebook') })).toHaveAttribute(
      'href',
      '/api/account/oauth2/authorization/facebook',
    );
    expect(screen.getByText(messages.or)).toBeInTheDocument();
  });

  it('given only Google enabled, when the registration page opens, then only its button shows', async () => {
    stubStore(['GOOGLE']);
    renderRoute('/cuenta/registro');

    expect(await screen.findByRole('link', { name: messages.continueWith('Google') })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: messages.continueWith('Facebook') })).not.toBeInTheDocument();
  });

  it('given no provider enabled, when the sign-in page opens, then it shows only the email form', async () => {
    const get = stubStore([]);
    renderRoute('/cuenta/ingresar');

    await vi.waitFor(() => {
      expect(get).toHaveBeenCalledWith('/api/account/oauth2/providers', expect.anything());
    });
    expect(screen.queryByRole('link', { name: /Continuar con/ })).not.toBeInTheDocument();
    expect(screen.queryByText(messages.or)).not.toBeInTheDocument();
  });

  it('given a Facebook link waiting for its email, when the sign-in page opens, then it says to open the email', () => {
    stubStore();
    renderRoute('/cuenta/ingresar?social=confirm-link');

    expect(screen.getByRole('status')).toHaveTextContent(messages.confirmLink);
  });

  it('given the provider failed, when the sign-in page opens, then it says so as an alert', () => {
    stubStore();
    renderRoute('/cuenta/ingresar?social=failed');

    expect(screen.getAllByRole('alert').some((alert) => alert.textContent === messages.failed)).toBe(true);
  });

  it('given an unknown value, when the sign-in page opens, then nothing extra is said', () => {
    stubStore();
    renderRoute('/cuenta/ingresar?social=whatever');

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});

describe('the email of a Facebook account that came without one (UC-27, T-E4-14)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  const page = es.account.completeEmail;

  async function giveEmail(email = 'ana@example.com') {
    await userEvent.type(screen.getByLabelText(es.account.email), email);
    await userEvent.click(screen.getByRole('button', { name: page.send }));
  }

  it('given the visitor, when it gives its email, then it is told a link may be on its way', async () => {
    stubStore();
    const post = stubPost(answer(204));
    renderRoute('/cuenta/completar-email');

    await giveEmail();

    const done = await screen.findByRole('status');
    expect(done).toHaveTextContent(page.done('ana@example.com'));
    expect(done).toHaveFocus();
    expect(post).toHaveBeenCalledWith('/api/account/oauth2/email', { body: { email: 'ana@example.com' } });
  });

  it('given the external account no longer waits, when it gives its email, then it is told to start again', async () => {
    stubStore();
    stubPost(answer(410, { problem: 'EXTERNAL_SIGN_IN_EXPIRED' }));
    renderRoute('/cuenta/completar-email');

    await giveEmail();

    expect(await screen.findByRole('status')).toHaveTextContent(page.expired);
  });

  it('given something that is not an email, when it is given, then the form says so', async () => {
    stubStore();
    stubPost(answer(422, { problem: 'INVALID_EMAIL' }));
    renderRoute('/cuenta/completar-email');

    await giveEmail('ana');

    expect(await screen.findByText(page.invalidEmail)).toBeInTheDocument();
  });
});

describe('the link of the link confirmation email (UC-27, T-E4-14)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  const page = es.account.link;

  async function pressConfirm() {
    await userEvent.click(await screen.findByRole('button', { name: page.confirm }));
  }

  it('given a link confirmation email, when its link is opened, then nothing is linked until the button is pressed', async () => {
    stubStore();
    const post = stubPost(answer(204));
    renderRoute(`/cuenta/vincular#${TOKEN}`);

    expect(await screen.findByRole('button', { name: page.confirm })).toBeInTheDocument();
    expect(screen.getByText(page.intro)).toBeInTheDocument();
    expect(screen.getByText(page.notYou)).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it('given a valid link, when the button is pressed, then the account is linked and Facebook is offered', async () => {
    stubStore();
    const post = stubPost(answer(204));
    renderRoute(`/cuenta/vincular#${TOKEN}`);

    await pressConfirm();

    const done = await screen.findByText(page.linked);
    expect(post).toHaveBeenCalledTimes(1);
    expect(post).toHaveBeenCalledWith('/api/account/link/confirm', { body: { token: TOKEN } });
    const linked = done.closest('[role="status"]');
    expect(linked).not.toBeNull();
    expect(
      await within(linked as HTMLElement).findByRole('link', { name: messages.continueWith('Facebook') }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: messages.continueWith('Google') })).not.toBeInTheDocument();
  });

  it('given a used or expired link, when the button is pressed, then it says so', async () => {
    stubStore();
    stubPost(answer(410, { problem: 'INVALID_LINK' }));
    renderRoute(`/cuenta/vincular#${TOKEN}`);

    await pressConfirm();

    expect(await screen.findByRole('alert')).toHaveTextContent(page.invalidLink);
  });

  it('given an external account linked elsewhere, when the button is pressed, then it says it could not link it', async () => {
    stubStore();
    stubPost(answer(409, { problem: 'EXTERNAL_ACCOUNT_ALREADY_LINKED' }));
    renderRoute(`/cuenta/vincular#${TOKEN}`);

    await pressConfirm();

    expect(await screen.findByRole('alert')).toHaveTextContent(page.alreadyLinked);
  });

  it('given no token, when the page opens, then there is no button, nothing is sent and the link is invalid', () => {
    stubStore();
    const post = stubPost(answer(204));
    renderRoute('/cuenta/vincular');

    expect(screen.getByRole('alert')).toHaveTextContent(page.invalidLink);
    expect(screen.queryByRole('button', { name: page.confirm })).not.toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });
});
