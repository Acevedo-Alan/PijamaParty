import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { problem, signedIn, signedOut, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const productList = answer(200, { items: [], hasMore: false });
const categoryList = answer(200, []);

async function signInWith(email: string, password: string) {
  await userEvent.type(await screen.findByLabelText(es.admin.signIn.email), email);
  await userEvent.type(screen.getByLabelText(es.admin.signIn.password), password);
  await userEvent.click(screen.getByRole('button', { name: es.admin.signIn.submit }));
}

describe('back-office sign-in (UC-04)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given correct credentials, when signing in, then the administrator reaches the page asked for', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedOut,
      'POST /api/admin/auth/login': signedIn,
      'GET /api/admin/catalog/products': productList,
      'GET /api/admin/catalog/categories': categoryList,
    });
    const { router } = renderRoute('/admin/productos');

    await signInWith('owner@example.com', 'a long password');

    expect(await screen.findByRole('heading', { level: 1, name: es.admin.productList.title })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/productos');
    expect(api.callsTo('POST /api/admin/auth/login')[0]?.options.body).toEqual({
      email: 'owner@example.com',
      password: 'a long password',
    });
  });

  it('given wrong credentials, when trying, then one generic message is shown and the password is cleared', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedOut, 'POST /api/admin/auth/login': problem(401) });
    renderRoute('/admin/ingresar');

    await signInWith('owner@example.com', 'wrong password');

    expect(await screen.findByRole('alert')).toHaveTextContent(es.admin.signIn.invalidCredentials);
    expect(screen.getByLabelText(es.admin.signIn.password)).toHaveValue('');
  });

  it('given a locked account, when trying, then the unlock time is shown in Argentina time', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedOut,
      'POST /api/admin/auth/login': problem(423, { lockedUntil: '2026-10-11T17:30:00Z' }),
    });
    renderRoute('/admin/ingresar');

    await signInWith('owner@example.com', 'any password');

    expect(await screen.findByRole('alert')).toHaveTextContent(es.admin.signIn.locked('14:30'));
  });

  it('given too many attempts from this IP, when trying, then it asks to wait', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedOut, 'POST /api/admin/auth/login': problem(429) });
    renderRoute('/admin/ingresar');

    await signInWith('owner@example.com', 'any password');

    expect(await screen.findByRole('alert')).toHaveTextContent(es.admin.signIn.tooManyAttempts);
  });

  it('given a user with role CUSTOMER, when opening /admin, then access is denied', async () => {
    stubApi({ 'GET /api/admin/auth/me': problem(403) });

    renderRoute('/admin');

    expect(await screen.findByRole('heading', { level: 1, name: es.admin.accessDenied.title })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: es.admin.nav })).not.toBeInTheDocument();
  });

  it('given a session that expires, when a back-office call answers 401, then the sign-in page opens with a notice', async () => {
    stubApi({
      'GET /api/admin/auth/me': [signedIn, signedOut],
      'GET /api/admin/catalog/categories': problem(401),
      'GET /api/admin/catalog/products': productList,
    });
    const { router } = renderRoute('/admin/categorias');

    expect(await screen.findByText(es.admin.signIn.expired)).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/ingresar');
  });

  it('given a signed-in administrator, when signing out, then the session ends and the sign-in page opens', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': [signedIn, signedOut],
      'POST /api/admin/auth/logout': answer(204),
      'GET /api/admin/catalog/products': productList,
      'GET /api/admin/catalog/categories': categoryList,
    });
    const { router } = renderRoute('/admin');

    await userEvent.click(await screen.findByRole('button', { name: es.admin.signOut }));

    expect(await screen.findByRole('heading', { level: 1, name: es.admin.signIn.title })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/ingresar');
    expect(api.callsTo('POST /api/admin/auth/logout')).toHaveLength(1);
  });
});
