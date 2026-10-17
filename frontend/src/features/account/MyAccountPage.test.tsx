import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { es } from '../../i18n/es.ts';
import { answer, NO_CART, withHeader } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { goToProvider } from './providerRedirect.ts';

vi.mock('./providerRedirect.ts', () => ({ goToProvider: vi.fn() }));

const messages = es.account.myAccount;
const ANA = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'ana@example.com',
  name: 'Ana Pérez',
  phone: null,
};
const GOOGLE = {
  provider: 'GOOGLE',
  email: 'ana@gmail.com',
  linkedAt: '2026-10-17T15:00:00Z',
};
const FACEBOOK = {
  provider: 'FACEBOOK',
  email: 'ana.perez@example.com',
  linkedAt: '2026-10-17T16:00:00Z',
};

interface Methods {
  hasPassword: boolean;
  identities: (typeof GOOGLE)[];
}

/** The customer is signed in, both providers are enabled, and GET /api/account/identities answers `methods` now. */
function stubMethods(initial: Methods) {
  const methods = { current: initial };
  vi.spyOn(api, 'GET').mockImplementation(
    withHeader(
      (path) => {
        switch (path) {
          case '/api/account/identities':
            return Promise.resolve(answer(200, methods.current));
          case '/api/account/oauth2/providers':
            return Promise.resolve(answer(200, { providers: ['GOOGLE', 'FACEBOOK'] }));
          default:
            return Promise.resolve(NO_CART);
        }
      },
      answer(200, ANA),
    ),
  );
  return methods;
}

/** The card of a sign-in method, by its heading text. */
async function card(name: string): Promise<HTMLElement> {
  const items = await screen.findAllByRole('listitem');
  const found = items.find((item) => within(item).queryByText(name, { selector: '.address-line' }) !== null);
  if (found === undefined) {
    throw new Error(`No card for ${name}`);
  }
  return found;
}

describe('my account (UC-27, T-E4-15)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.mocked(goToProvider).mockClear();
  });

  it('given a visitor who is not signed in, when it opens my account, then it is sent to sign in', async () => {
    vi.spyOn(api, 'GET').mockImplementation(withHeader(() => Promise.resolve(NO_CART)));
    const { router } = renderRoute('/cuenta');

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/cuenta/ingresar');
    });
  });

  it('given a signed-in customer, when it follows the header link, then it sees how it signs in', async () => {
    stubMethods({ hasPassword: false, identities: [GOOGLE] });
    const { router } = renderRoute('/');

    await userEvent.click(await screen.findByRole('link', { name: es.account.myAccountLink }));

    expect(router.state.location.pathname).toBe('/cuenta');
    expect(await screen.findByText(messages.passwordMissing)).toBeInTheDocument();
    expect(within(await card('Google')).getByText(messages.linkedAs('ana@gmail.com'))).toBeInTheDocument();
    const facebook = await card('Facebook');
    expect(within(facebook).getByText(messages.notLinked)).toBeInTheDocument();
    expect(
      within(facebook).getByRole('button', {
        name: messages.linkProvider('Facebook'),
      }),
    ).toBeInTheDocument();
  });

  it('given a customer whose only sign-in method is Google, when it unlinks it, then it is refused and offered to create a password', async () => {
    stubMethods({ hasPassword: false, identities: [GOOGLE] });
    const remove = vi.spyOn(api, 'DELETE').mockResolvedValue(answer(409, { problem: 'LAST_SIGN_IN_METHOD' }));
    renderRoute('/cuenta');

    await userEvent.click(
      await screen.findByRole('button', {
        name: messages.unlinkProvider('Google'),
      }),
    );
    expect(remove).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: messages.confirmUnlinkYes }));

    expect(remove).toHaveBeenCalledWith('/api/account/identities/{provider}', {
      params: { path: { provider: 'google' } },
    });
    expect(await screen.findByText(messages.lastSignInMethod('Google'))).toBeInTheDocument();
    expect(screen.getByLabelText(messages.newPassword)).toBeInTheDocument();
  });

  it('given a customer without a password, when it creates one, then it is sent and the page says it can sign in with it', async () => {
    const methods = stubMethods({ hasPassword: false, identities: [GOOGLE] });
    const put = vi.spyOn(api, 'PUT').mockImplementation((() => {
      methods.current = { ...methods.current, hasPassword: true };
      return Promise.resolve(answer(204));
    }) as never);
    renderRoute('/cuenta');

    await userEvent.click(await screen.findByRole('button', { name: messages.createPassword }));
    await userEvent.type(screen.getByLabelText(messages.newPassword), 'ocho1234');
    await userEvent.click(screen.getByRole('button', { name: messages.savePassword }));

    expect(put).toHaveBeenCalledWith('/api/account/password', {
      body: { password: 'ocho1234' },
    });
    expect(await screen.findByText(messages.passwordCreated)).toBeInTheDocument();
    expect(await screen.findByText(messages.passwordSet)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: messages.createPassword })).not.toBeInTheDocument();
  });

  it('given a short password, when the customer creates it, then it is told why and stays on the form', async () => {
    stubMethods({ hasPassword: false, identities: [GOOGLE] });
    vi.spyOn(api, 'PUT').mockResolvedValue(answer(422, { problem: 'PASSWORD_TOO_SHORT' }));
    renderRoute('/cuenta');

    await userEvent.click(await screen.findByRole('button', { name: messages.createPassword }));
    await userEvent.type(screen.getByLabelText(messages.newPassword), 'corta');
    await userEvent.click(screen.getByRole('button', { name: messages.savePassword }));

    expect(await screen.findByText(messages.passwordTooShort)).toBeInTheDocument();
    expect(screen.getByLabelText(messages.newPassword)).toBeInTheDocument();
  });

  it('given a customer with a password and Facebook, when it unlinks Facebook, then the page says so', async () => {
    const methods = stubMethods({ hasPassword: true, identities: [FACEBOOK] });
    vi.spyOn(api, 'DELETE').mockImplementation((() => {
      methods.current = { hasPassword: true, identities: [] };
      return Promise.resolve(answer(204));
    }) as never);
    renderRoute('/cuenta');

    await userEvent.click(
      await screen.findByRole('button', {
        name: messages.unlinkProvider('Facebook'),
      }),
    );
    await userEvent.click(screen.getByRole('button', { name: messages.confirmUnlinkYes }));

    expect(await screen.findByText(messages.unlinked('Facebook'))).toBeInTheDocument();
    expect(within(await card('Facebook')).getByText(messages.notLinked)).toBeInTheDocument();
  });

  it('given a provider not linked, when the customer links it, then the session is told first and then it goes to the provider', async () => {
    stubMethods({ hasPassword: true, identities: [] });
    const post = vi.spyOn(api, 'POST').mockResolvedValue(answer(204));
    renderRoute('/cuenta');

    await userEvent.click(
      await screen.findByRole('button', {
        name: messages.linkProvider('Facebook'),
      }),
    );

    expect(post).toHaveBeenCalledWith('/api/account/oauth2/link/{provider}', {
      params: { path: { provider: 'facebook' } },
    });
    await waitFor(() => {
      expect(goToProvider).toHaveBeenCalledWith('/api/account/oauth2/authorization/facebook');
    });
  });

  it('given the request to link fails, when the customer links a provider, then it stays and is told', async () => {
    stubMethods({ hasPassword: true, identities: [] });
    vi.spyOn(api, 'POST').mockResolvedValue(answer(500));
    renderRoute('/cuenta');

    await userEvent.click(
      await screen.findByRole('button', {
        name: messages.linkProvider('Google'),
      }),
    );

    expect(await screen.findByText(messages.linkError)).toBeInTheDocument();
    expect(goToProvider).not.toHaveBeenCalled();
  });

  it.each([
    ['linked', messages.social.linked],
    ['already-linked', messages.social.alreadyLinked],
    ['cancelled', messages.social.cancelled],
    ['failed', messages.social.failed],
  ])(
    'given the provider sent the customer back with %s, when the page opens, then it says so',
    async (social, text) => {
      stubMethods({ hasPassword: true, identities: [] });
      renderRoute(`/cuenta?social=${social}`);

      expect(await screen.findByText(text)).toBeInTheDocument();
    },
  );
});
