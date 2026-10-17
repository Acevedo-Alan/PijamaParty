import { api } from '../../api/client.ts';
import { problemProperty } from '../admin/adminSession.ts';
import type { SignInProvider } from './accountApi.ts';

/** The name the browser saves the download as (UC-28); the server says the same in Content-Disposition. */
export const DATA_FILE_NAME = 'mis-datos.json';

/** GET /api/account/data (UC-28, T-E4-16): the customer's data as one JSON file. */
export type DownloadAnswer = { status: 'downloaded'; file: Blob } | { status: 'signedOut' };

/** POST /api/account/oauth2/reauthenticate/{provider}: the session waits for the provider's return. */
export type ReauthenticationAnswer =
  { status: 'requested' } | { status: 'notLinked' } | { status: 'notEnabled' } | { status: 'signedOut' };

/** DELETE /api/account: deleted and signed out, or why not. */
export type DeleteAccountAnswer =
  | { status: 'deleted' }
  | { status: 'wrongPassword' }
  | { status: 'reauthenticationRequired' }
  | { status: 'signedOut' };

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

export async function downloadCustomerData(): Promise<DownloadAnswer> {
  const { data, response } = await api.GET('/api/account/data', { parseAs: 'blob' });
  if (response.status === 401 || response.status === 403) {
    return { status: 'signedOut' };
  }
  if (response.status !== 200 || data === undefined) {
    throw failed('Downloading the data', response);
  }
  return { status: 'downloaded', file: data };
}

/** Only after this answers does the storefront navigate to the provider (signInWithProviderUrl). */
export async function requestReauthentication(provider: SignInProvider): Promise<ReauthenticationAnswer> {
  const { error, response } = await api.POST('/api/account/oauth2/reauthenticate/{provider}', {
    params: { path: { provider: provider.toLowerCase() } },
  });
  switch (response.status) {
    case 204:
      return { status: 'requested' };
    case 401:
      return { status: 'signedOut' };
    case 404:
      return problemProperty(error, 'problem') === 'IDENTITY_NOT_FOUND'
        ? { status: 'notLinked' }
        : { status: 'notEnabled' };
    default:
      throw failed('Asking to confirm with a provider', response);
  }
}

/**
 * Without a password, the session must have come back from a linked provider less than 5 minutes ago. The deletion
 * signs out: the caller forgets everything of the account.
 */
export async function deleteAccount(password: string | null): Promise<DeleteAccountAnswer> {
  const { error, response } = await api.DELETE('/api/account', {
    body: password === null ? {} : { password },
  });
  switch (response.status) {
    case 204:
      return { status: 'deleted' };
    case 401:
      return { status: 'signedOut' };
    case 403: {
      const problem = problemProperty(error, 'problem');
      if (problem === 'WRONG_PASSWORD') {
        return { status: 'wrongPassword' };
      }
      if (problem === 'REAUTHENTICATION_REQUIRED') {
        return { status: 'reauthenticationRequired' };
      }
      // Any other 403 is the CSRF filter: the session is gone.
      return { status: 'signedOut' };
    }
    default:
      throw failed('Deleting the account', response);
  }
}
