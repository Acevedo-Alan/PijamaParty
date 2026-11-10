import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { answer, NO_CART, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import chibiCss from './authChibi.css?raw';

const ANA = { id: '00000000-0000-0000-0000-000000000001', email: 'ana@example.com', name: 'Ana Pérez' };

function stubStore() {
  vi.spyOn(api, 'GET').mockImplementation(withHeader(() => Promise.resolve(NO_CART)));
}

function stubPost(response: unknown) {
  return vi.spyOn(api, 'POST').mockImplementation((() => Promise.resolve(response)) as never);
}

/** The chibi's sky, which carries what it is doing. */
function chibi(): HTMLElement {
  const sky = document.querySelector<HTMLElement>('.auth-sky');
  if (sky === null) {
    throw new Error('no chibi on the page');
  }
  return sky;
}

describe('the chibi of the account pages (UC-19, T-E7-47)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it.each(['/cuenta/ingresar', '/cuenta/registro', '/cuenta/recuperar', '/cuenta/restablecer#token'])(
    'given %s, when it opens, then the chibi is beside the card, idle and hidden from screen readers',
    async (path) => {
      stubStore();
      renderRoute(path);

      await screen.findByRole('heading', { level: 1 });
      expect(chibi()).toHaveAttribute('aria-hidden', 'true');
      expect(chibi().nextElementSibling).toHaveClass('auth-panel');
      expect(chibi()).toHaveAttribute('data-mood', 'idle');
      expect(chibi()).toHaveTextContent(/z+Z$/i);
    },
  );

  it.each([
    ['/cuenta/ingresar', 'signIn', false, false],
    ['/cuenta/registro', 'register', false, false],
    ['/cuenta/recuperar', 'recover', true, false],
    ['/cuenta/restablecer#token', 'reset', false, true],
  ])(
    'given %s, when it opens, then the chibi takes the pose %s, with the question mark %s and the key %s',
    async (path, pose, wonders, holdsKey) => {
      stubStore();
      renderRoute(path);

      await screen.findByRole('heading', { level: 1 });
      expect(chibi()).toHaveAttribute('data-pose', pose);
      expect(chibi().querySelector('.chibi-wonder') !== null).toBe(wonders);
      expect(chibi().querySelector('.chibi-key') !== null).toBe(holdsKey);
      expect(chibi().querySelectorAll('.auth-rain-star')).toHaveLength(10);
    },
  );

  it('given the sign-in page, when it opens, then the store sign stands in front of the chibi, with its size and no text', async () => {
    stubStore();
    renderRoute('/cuenta/ingresar');

    await screen.findByRole('heading', { level: 1 });
    const signs = [...chibi().querySelectorAll('img')];
    expect(signs).toHaveLength(1);
    const [sign] = signs;
    expect(sign).toHaveClass('auth-logo');
    expect(sign).toHaveAttribute('alt', '');
    expect(sign).toHaveAttribute('width', '407');
    expect(sign).toHaveAttribute('height', '345');
    const stage = chibi().querySelector('.auth-stage');
    expect(stage?.lastElementChild).toBe(sign);
    expect(stage?.firstElementChild).toHaveClass('chibi');
    expect(within(chibi()).queryAllByRole('img', { hidden: true })).toEqual([]);
  });

  it('given the sign-in page, when the focus moves through the form, then the chibi looks, hides its eyes and peeks', async () => {
    stubStore();
    renderRoute('/cuenta/ingresar');

    await userEvent.click(await screen.findByLabelText(es.account.email));
    expect(chibi()).toHaveAttribute('data-mood', 'text');

    await userEvent.click(screen.getByLabelText(es.account.password));
    expect(chibi()).toHaveAttribute('data-mood', 'password');

    await userEvent.click(screen.getByRole('button', { name: es.account.showPassword }));
    expect(chibi()).toHaveAttribute('data-mood', 'peek');

    await userEvent.click(screen.getByLabelText(es.account.email));
    expect(chibi()).toHaveAttribute('data-mood', 'text');

    await userEvent.click(screen.getByRole('heading', { level: 1 }));
    expect(chibi()).toHaveAttribute('data-mood', 'idle');
  });

  it('given the sign-in page, when the API refuses the credentials, then the chibi is sad until a field is typed in', async () => {
    stubStore();
    stubPost(answer(401, { problem: 'INVALID_CREDENTIALS' }));
    renderRoute('/cuenta/ingresar');

    await userEvent.type(await screen.findByLabelText(es.account.email), 'ana@example.com');
    await userEvent.type(screen.getByLabelText(es.account.password), 'mala1234');
    await userEvent.click(screen.getByRole('button', { name: es.account.signIn.send }));

    await waitFor(() => {
      expect(chibi()).toHaveAttribute('data-mood', 'error');
    });
    await userEvent.click(screen.getByLabelText(es.account.password));
    expect(chibi()).toHaveAttribute('data-mood', 'error');

    await userEvent.type(screen.getByLabelText(es.account.password), 'x');
    expect(chibi()).toHaveAttribute('data-mood', 'password');
  });

  it('given the registration page, when the account is created, then the chibi celebrates', async () => {
    stubStore();
    stubPost(answer(201));
    renderRoute('/cuenta/registro');

    await userEvent.type(await screen.findByLabelText(es.account.register.name), 'Ana Pérez');
    await userEvent.type(screen.getByLabelText(es.account.email), 'ana@example.com');
    await userEvent.type(screen.getByLabelText(es.account.password), 'ocho1234');
    await userEvent.click(screen.getByRole('button', { name: es.account.register.send }));

    await screen.findByRole('status');
    expect(chibi()).toHaveAttribute('data-mood', 'success');
  });

  it('given the sign-in page, when the sign-in succeeds, then the next page opens with no wait for the chibi', async () => {
    stubStore();
    stubPost(answer(200, ANA));
    const { router } = renderRoute('/cuenta/ingresar');

    await userEvent.type(await screen.findByLabelText(es.account.email), 'ana@example.com');
    await userEvent.type(screen.getByLabelText(es.account.password), 'ocho1234');
    await userEvent.click(screen.getByRole('button', { name: es.account.signIn.send }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/');
    });
  });

  it('given a reset link without its token, when it opens, then the chibi is sad about the invalid link', async () => {
    stubStore();
    renderRoute('/cuenta/restablecer');

    await screen.findByText(es.account.reset.invalidLink);
    expect(chibi()).toHaveAttribute('data-mood', 'error');
  });

  it('given reduced motion, when the stylesheet is read, then every loop, hop and shake is only for no-preference', () => {
    const [, moving = ''] = /@media \(prefers-reduced-motion: no-preference\) \{([\s\S]*?)\n\}/.exec(chibiCss) ?? [];
    const outside = chibiCss.replace(moving, '');

    expect(moving).toMatch(/animation:/);
    expect(outside).not.toMatch(/(?<![\w-])animation:/);
  });
});
