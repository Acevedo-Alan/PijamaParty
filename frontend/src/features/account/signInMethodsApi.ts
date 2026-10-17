import { api } from '../../api/client.ts';
import { problemProperty } from '../admin/adminSession.ts';
import type { SignInProvider } from './accountApi.ts';

/** The query key of the sign-in methods; every change refetches them, and signing out forgets them. */
export const SIGN_IN_METHODS_KEY = ['account', 'sign-in-methods'] as const;

/** An external account linked to the customer (UC-27), with the email it was linked with. */
export interface LinkedIdentity {
  provider: SignInProvider;
  email: string;
  linkedAt: Date;
}

/** GET /api/account/identities (UC-27, T-E4-15): how the customer signs in, Google first. */
export interface SignInMethods {
  hasPassword: boolean;
  identities: LinkedIdentity[];
}

/** DELETE /api/account/identities/{provider}. */
export type UnlinkAnswer =
  { status: 'unlinked' } | { status: 'notLinked' } | { status: 'lastSignInMethod' } | { status: 'signedOut' };

/** PUT /api/account/password: it only creates a password; changing one goes through the recovery. */
export type CreatePasswordAnswer =
  | { status: 'created' }
  | { status: 'alreadySet' }
  | { status: 'passwordTooShort' }
  | { status: 'passwordTooLong' }
  | { status: 'signedOut' };

/** POST /api/account/oauth2/link/{provider}: the session waits for the provider's return. */
export type LinkRequestAnswer = { status: 'requested' } | { status: 'notEnabled' } | { status: 'signedOut' };

const PROVIDERS: readonly SignInProvider[] = ['GOOGLE', 'FACEBOOK'];

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

function isProvider(value: unknown): value is SignInProvider {
  return PROVIDERS.some((provider) => provider === value);
}

/** The customer's sign-in methods, or null when nobody is signed in as a customer. */
export async function fetchSignInMethods(signal?: AbortSignal): Promise<SignInMethods | null> {
  const { data, response } = await api.GET('/api/account/identities', {
    signal: signal ?? null,
  });
  if (response.status === 401 || response.status === 403) {
    return null;
  }
  if (response.status !== 200 || data?.hasPassword === undefined || !Array.isArray(data.identities)) {
    throw failed('Sign-in methods', response);
  }
  const identities = data.identities.map((identity) => {
    if (!isProvider(identity.provider) || identity.email === undefined || identity.linkedAt === undefined) {
      throw failed('Sign-in methods', response);
    }
    return {
      provider: identity.provider,
      email: identity.email,
      linkedAt: new Date(identity.linkedAt),
    };
  });
  return { hasPassword: data.hasPassword, identities };
}

export async function unlinkProvider(provider: SignInProvider): Promise<UnlinkAnswer> {
  const { response } = await api.DELETE('/api/account/identities/{provider}', {
    params: { path: { provider: provider.toLowerCase() } },
  });
  switch (response.status) {
    case 204:
      return { status: 'unlinked' };
    case 401:
      return { status: 'signedOut' };
    case 404:
      return { status: 'notLinked' };
    case 409:
      return { status: 'lastSignInMethod' };
    default:
      throw failed('Unlinking a provider', response);
  }
}

export async function createPassword(password: string): Promise<CreatePasswordAnswer> {
  const { error, response } = await api.PUT('/api/account/password', {
    body: { password },
  });
  switch (response.status) {
    case 204:
      return { status: 'created' };
    case 401:
      return { status: 'signedOut' };
    case 409:
      return { status: 'alreadySet' };
    case 422: {
      const problem = problemProperty(error, 'problem');
      if (problem === 'PASSWORD_TOO_SHORT') {
        return { status: 'passwordTooShort' };
      }
      if (problem === 'PASSWORD_TOO_LONG') {
        return { status: 'passwordTooLong' };
      }
      throw failed('Creating a password', response);
    }
    default:
      throw failed('Creating a password', response);
  }
}

/** Only after this answers does the storefront navigate to the provider (signInWithProviderUrl). */
export async function requestLink(provider: SignInProvider): Promise<LinkRequestAnswer> {
  const { response } = await api.POST('/api/account/oauth2/link/{provider}', {
    params: { path: { provider: provider.toLowerCase() } },
  });
  switch (response.status) {
    case 204:
      return { status: 'requested' };
    case 401:
      return { status: 'signedOut' };
    case 404:
      return { status: 'notEnabled' };
    default:
      throw failed('Asking to link a provider', response);
  }
}
