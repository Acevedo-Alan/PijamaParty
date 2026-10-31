import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { problem, signedIn, signedOut, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';

const messages = esAdmin.signIn;
const productList = answer(200, { items: [], hasMore: false });
const categoryList = answer(200, []);

async function signInWith(email: string, password: string) {
  await userEvent.type(await screen.findByLabelText(esAdmin.signIn.email), email);
  await userEvent.type(screen.getByLabelText(esAdmin.signIn.password), password);
  await userEvent.click(screen.getByRole('button', { name: esAdmin.signIn.submit }));
}

describe('back-office sign-in (UC-04)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given correct credentials and a code of the app, when signing in, then the administrator reaches the page asked for', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedOut,
      'POST /api/admin/auth/login': answer(200, { nextStep: 'SECOND_FACTOR' }),
      'POST /api/admin/auth/second-factor': signedIn,
      'GET /api/admin/catalog/products': productList,
      'GET /api/admin/catalog/categories': categoryList,
    });
    const { router } = renderRoute('/admin/productos');

    await signInWith('owner@example.com', 'a long password');
    expect(await screen.findByRole('heading', { level: 1, name: messages.codeTitle })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(messages.codeLabel), '123 456');
    await userEvent.click(screen.getByRole('button', { name: messages.verify }));

    expect(await screen.findByRole('heading', { level: 1, name: esAdmin.productList.title })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/productos');
    expect(api.callsTo('POST /api/admin/auth/login')[0]?.options.body).toEqual({
      email: 'owner@example.com',
      password: 'a long password',
    });
    expect(api.callsTo('POST /api/admin/auth/second-factor')[0]?.options.body).toEqual({ code: '123 456' });
  });

  it('given the right password and a wrong code, when verifying, then the back office stays closed', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedOut,
      'POST /api/admin/auth/login': answer(200, { nextStep: 'SECOND_FACTOR' }),
      'POST /api/admin/auth/second-factor': problem(401),
    });
    renderRoute('/admin/ingresar');

    await signInWith('owner@example.com', 'a long password');
    await userEvent.type(await screen.findByLabelText(messages.codeLabel), '000000');
    await userEvent.click(screen.getByRole('button', { name: messages.verify }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.invalidCode);
    expect(screen.getByLabelText(messages.codeLabel)).toHaveValue('');
    expect(screen.getByRole('heading', { level: 1, name: messages.codeTitle })).toBeInTheDocument();
  });

  it('given more than 5 minutes after the password, when the code arrives, then the password is asked again', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedOut,
      'POST /api/admin/auth/login': answer(200, { nextStep: 'SECOND_FACTOR' }),
      'POST /api/admin/auth/second-factor': problem(409),
    });
    renderRoute('/admin/ingresar');

    await signInWith('owner@example.com', 'a long password');
    await userEvent.type(await screen.findByLabelText(messages.codeLabel), '123456');
    await userEvent.click(screen.getByRole('button', { name: messages.verify }));

    expect(await screen.findByText(messages.expiredStep)).toBeInTheDocument();
    expect(screen.getByLabelText(messages.password)).toBeInTheDocument();
  });

  it('given no second factor yet, when the password is right, then the app is enrolled and the recovery codes shown once', async () => {
    const api = stubApi({
      'GET /api/admin/auth/me': signedOut,
      'POST /api/admin/auth/login': answer(200, { nextStep: 'ENROLLMENT' }),
      'GET /api/admin/auth/enrollment': answer(200, {
        secret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
        otpauthUri: 'otpauth://totp/x',
        qrCode: 'data:image/png;base64,AAAA',
      }),
      'POST /api/admin/auth/enrollment': [
        problem(401),
        answer(200, {
          administrator: { id: 'admin-1', email: 'owner@example.com', name: 'Dueña' },
          recoveryCodes: ['AB7K3-M9QX2', 'ZZZZZ-00000'],
        }),
      ],
      'GET /api/admin/catalog/products': productList,
      'GET /api/admin/catalog/categories': categoryList,
    });
    const { router } = renderRoute('/admin/ingresar');

    await signInWith('owner@example.com', 'a long password');
    expect(await screen.findByRole('img', { name: messages.qrAlt })).toHaveAttribute('src', 'data:image/png;base64,AAAA');
    expect(screen.getByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(messages.enrollCodeLabel), '000000');
    await userEvent.click(screen.getByRole('button', { name: messages.enroll }));
    expect(await screen.findByRole('alert')).toHaveTextContent(messages.invalidCode);
    await userEvent.type(screen.getByLabelText(messages.enrollCodeLabel), '654321');
    await userEvent.click(screen.getByRole('button', { name: messages.enroll }));

    const codes = await screen.findByRole('list', { name: messages.recoveryTitle });
    expect(within(codes).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'AB7K3-M9QX2',
      'ZZZZZ-00000',
    ]);
    const enter = screen.getByRole('button', { name: messages.continue });
    expect(enter).toBeDisabled();
    await userEvent.click(screen.getByLabelText(messages.saved));
    await userEvent.click(enter);

    expect(await screen.findByRole('heading', { level: 1, name: esAdmin.productList.title })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/productos');
    expect(api.callsTo('GET /api/admin/auth/enrollment')).toHaveLength(1);
  });

  it('given wrong credentials, when trying, then one generic message is shown and the password is cleared', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedOut, 'POST /api/admin/auth/login': problem(401) });
    renderRoute('/admin/ingresar');

    await signInWith('owner@example.com', 'wrong password');

    expect(await screen.findByRole('alert')).toHaveTextContent(esAdmin.signIn.invalidCredentials);
    expect(screen.getByLabelText(esAdmin.signIn.password)).toHaveValue('');
  });

  it('given a locked account, when trying, then the unlock time is shown in Argentina time', async () => {
    stubApi({
      'GET /api/admin/auth/me': signedOut,
      'POST /api/admin/auth/login': problem(423, { lockedUntil: '2026-10-11T17:30:00Z' }),
    });
    renderRoute('/admin/ingresar');

    await signInWith('owner@example.com', 'any password');

    expect(await screen.findByRole('alert')).toHaveTextContent(esAdmin.signIn.locked('14:30'));
  });

  it('given too many attempts from this IP, when trying, then it asks to wait', async () => {
    stubApi({ 'GET /api/admin/auth/me': signedOut, 'POST /api/admin/auth/login': problem(429) });
    renderRoute('/admin/ingresar');

    await signInWith('owner@example.com', 'any password');

    expect(await screen.findByRole('alert')).toHaveTextContent(esAdmin.signIn.tooManyAttempts);
  });

  it('given a user with role CUSTOMER, when opening /admin, then access is denied', async () => {
    stubApi({ 'GET /api/admin/auth/me': problem(403) });

    renderRoute('/admin');

    expect(await screen.findByRole('heading', { level: 1, name: esAdmin.accessDenied.title })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: esAdmin.nav })).not.toBeInTheDocument();
  });

  it('given a session that expires, when a back-office call answers 401, then the sign-in page opens with a notice', async () => {
    stubApi({
      'GET /api/admin/auth/me': [signedIn, signedOut],
      'GET /api/admin/catalog/categories': problem(401),
      'GET /api/admin/catalog/products': productList,
    });
    const { router } = renderRoute('/admin/categorias');

    expect(await screen.findByText(esAdmin.signIn.expired)).toBeInTheDocument();
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

    // As on a phone: "Cerrar sesión" is in the menu's panel.
    await userEvent.click(await screen.findByRole('button', { name: esAdmin.menu }));
    await userEvent.click(screen.getByRole('button', { name: esAdmin.signOut }));

    expect(await screen.findByRole('heading', { level: 1, name: esAdmin.signIn.title })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/ingresar');
    expect(api.callsTo('POST /api/admin/auth/logout')).toHaveLength(1);
  });
});
