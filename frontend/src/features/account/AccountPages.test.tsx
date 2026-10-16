import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { formatTime } from '../../i18n/formatDate.ts';
import { answer, NO_CART, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const TOKEN = 'A'.repeat(42) + '1';
const ANA = { id: '00000000-0000-0000-0000-000000000001', email: 'ana@example.com', name: 'Ana Pérez' };

/** Every GET of the storefront answers an empty cart; the header asks who is signed in. */
function stubStore(account?: unknown) {
  return vi.spyOn(api, 'GET').mockImplementation(withHeader(() => Promise.resolve(NO_CART), account));
}

function stubPost(...answers: unknown[]) {
  const queue = [...answers];
  return vi.spyOn(api, 'POST').mockImplementation((() =>
    Promise.resolve(queue.length > 1 ? queue.shift() : queue[0])) as never);
}

async function fillRegistration(name = 'Ana Pérez', email = 'ana@example.com', password = 'ocho1234') {
  await userEvent.type(screen.getByLabelText(es.account.register.name), name);
  await userEvent.type(screen.getByLabelText(es.account.email), email);
  await userEvent.type(screen.getByLabelText(es.account.password), password);
  await userEvent.click(screen.getByRole('button', { name: es.account.register.send }));
}

async function fillSignIn(email = 'ana@example.com', password = 'ocho1234') {
  await userEvent.type(screen.getByLabelText(es.account.email), email);
  await userEvent.type(screen.getByLabelText(es.account.password), password);
  await userEvent.click(screen.getByRole('button', { name: es.account.signIn.send }));
}

describe('customer registration, email verification and sign-in (UC-19, T-E4-02)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a visitor, when it registers, then it is told where the verification link went and is not signed in', async () => {
    stubStore();
    const post = stubPost(answer(201));
    renderRoute('/cuenta/registro');

    await fillRegistration();

    const done = await screen.findByRole('status');
    expect(done).toHaveTextContent(es.account.register.done('ana@example.com'));
    expect(done).toHaveFocus();
    expect(post).toHaveBeenCalledWith('/api/account/register', {
      body: { email: 'ana@example.com', password: 'ocho1234', name: 'Ana Pérez' },
    });
    expect(screen.getByRole('link', { name: es.account.signInLink })).toBeInTheDocument();
  });

  it('given an email already registered, when someone registers with it, then it is refused and offered to sign in', async () => {
    stubStore();
    stubPost(answer(409, { problem: 'EMAIL_ALREADY_REGISTERED' }));
    const { router } = renderRoute('/cuenta/registro');

    await fillRegistration();

    const alert = await screen.findByRole('alert');
    await waitFor(() => {
      expect(alert).toHaveTextContent(es.account.register.emailTaken);
    });
    await userEvent.click(within(alert).getByRole('link', { name: es.account.register.emailTakenAction }));
    expect(router.state.location.pathname).toBe('/cuenta/ingresar');
  });

  it.each([
    ['PASSWORD_TOO_SHORT', es.account.register.passwordTooShort],
    ['PASSWORD_TOO_LONG', es.account.register.passwordTooLong],
    ['INVALID_EMAIL', es.account.register.invalidEmail],
    ['INVALID_NAME', es.account.register.invalidName],
  ])('given a %s answer, when registering, then the form stays and says why', async (problem, message) => {
    stubStore();
    stubPost(answer(422, { problem }));
    renderRoute('/cuenta/registro');

    await fillRegistration();

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(message);
    });
    expect(screen.getByRole('button', { name: es.account.register.send })).toBeEnabled();
  });

  it('given a verification link, when it opens, then the token is used once and the sign-in says the email is verified', async () => {
    stubStore();
    const post = stubPost(answer(204));
    const { router } = renderRoute(`/cuenta/verificar#${TOKEN}`);

    expect(await screen.findByText(es.account.signIn.verified)).toHaveAttribute('role', 'status');
    expect(router.state.location.pathname).toBe('/cuenta/ingresar');
    expect(router.state.location.hash).toBe('');
    expect(post).toHaveBeenCalledOnce();
    expect(post).toHaveBeenCalledWith('/api/account/verify-email', { body: { token: TOKEN } });
    expect(screen.getByLabelText(es.account.email)).toBeInTheDocument();
  });

  it('given a used or expired link, when it opens, then it is refused and offered to sign in for a new one', async () => {
    stubStore();
    stubPost(answer(410, { problem: 'INVALID_LINK' }));
    renderRoute(`/cuenta/verificar#${TOKEN}`);

    expect(await screen.findByRole('alert')).toHaveTextContent(es.account.verify.invalidLink);
    expect(screen.getByRole('link', { name: es.account.verify.signIn })).toHaveAttribute('href', '/cuenta/ingresar');
  });

  it('given a link without its token, when it opens, then it is refused without asking the server', async () => {
    stubStore();
    const post = stubPost(answer(204));
    renderRoute('/cuenta/verificar');

    expect(await screen.findByRole('alert')).toHaveTextContent(es.account.verify.invalidLink);
    expect(post).not.toHaveBeenCalled();
  });

  it('given a verified customer, when it signs in, then it goes to the shop and the header greets it', async () => {
    stubStore();
    const post = stubPost(answer(200, ANA));
    const { router } = renderRoute('/cuenta/ingresar');

    await fillSignIn();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/');
    });
    expect(post).toHaveBeenCalledWith('/api/account/login', {
      body: { email: 'ana@example.com', password: 'ocho1234' },
    });
    expect(await screen.findByText(es.account.greeting('Ana'))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: es.account.signOut })).toBeInTheDocument();
  });

  it.each([
    [401, undefined, es.account.signIn.invalidCredentials],
    [403, { problem: 'EMAIL_NOT_VERIFIED' }, es.account.signIn.emailNotVerified],
    [429, undefined, es.account.tooManyAttempts],
  ])('given a %i answer, when signing in, then it stays and says why', async (status, body, message) => {
    stubStore();
    stubPost(answer(status, body));
    const { router } = renderRoute('/cuenta/ingresar');

    await fillSignIn();

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(message);
    });
    expect(router.state.location.pathname).toBe('/cuenta/ingresar');
    expect(screen.getByLabelText(es.account.password)).toHaveValue('');
  });

  it('given a locked account, when signing in, then it says until when', async () => {
    const until = '2026-10-16T18:15:00Z';
    stubStore();
    stubPost(answer(423, { lockedUntil: until }));
    renderRoute('/cuenta/ingresar');

    await fillSignIn();

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(es.account.signIn.locked(formatTime(new Date(until))));
    });
  });

  it('given a signed-in customer, when it signs out, then the header offers to sign in again', async () => {
    stubStore(answer(200, ANA));
    const post = stubPost(answer(204));
    renderRoute('/');

    await userEvent.click(await screen.findByRole('button', { name: es.account.signOut }));

    expect(await screen.findByRole('link', { name: es.account.signInLink })).toHaveAttribute('href', '/cuenta/ingresar');
    expect(post).toHaveBeenCalledWith('/api/account/logout');
    expect(screen.queryByText(es.account.greeting('Ana'))).not.toBeInTheDocument();
  });
});
