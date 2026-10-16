import { api } from '../../api/client.ts';
import { problemProperty } from '../admin/adminSession.ts';

/** The signed-in customer, as GET /api/account/me and the sign-in answer it (UC-19). */
export interface Customer {
  id: string;
  email: string;
  name: string;
}

/** POST /api/account/register (UC-19, T-E4-02): the account waits for its email to be verified. */
export type RegisterAnswer =
  | { status: 'registered' }
  | { status: 'emailTaken' }
  | { status: 'invalidEmail' }
  | { status: 'invalidName' }
  | { status: 'passwordTooShort' }
  | { status: 'passwordTooLong' }
  | { status: 'tooManyAttempts' };

/** POST /api/account/login (UC-19). */
export type SignInAnswer =
  | { status: 'signedIn'; customer: Customer }
  | { status: 'invalidCredentials' }
  | { status: 'emailNotVerified' }
  | { status: 'locked'; until: Date }
  | { status: 'tooManyAttempts' };

/** POST /api/account/verify-email (UC-19). */
export type VerifyAnswer = { status: 'verified' } | { status: 'invalidLink' };

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

function toCustomer(data: { id?: string; email?: string; name?: string } | undefined): Customer | null {
  if (data?.id === undefined || data.email === undefined || data.name === undefined) {
    return null;
  }
  return { id: data.id, email: data.email, name: data.name };
}

const REGISTRATION_PROBLEMS: Record<string, RegisterAnswer> = {
  INVALID_EMAIL: { status: 'invalidEmail' },
  INVALID_NAME: { status: 'invalidName' },
  PASSWORD_TOO_SHORT: { status: 'passwordTooShort' },
  PASSWORD_TOO_LONG: { status: 'passwordTooLong' },
};

/** The fields go as typed: the server trims the name and normalizes the email. */
export async function register(email: string, password: string, name: string): Promise<RegisterAnswer> {
  const { error, response } = await api.POST('/api/account/register', { body: { email, password, name } });
  switch (response.status) {
    case 201:
      return { status: 'registered' };
    case 409:
      return { status: 'emailTaken' };
    case 422: {
      const problem = problemProperty(error, 'problem');
      const answer = typeof problem === 'string' ? REGISTRATION_PROBLEMS[problem] : undefined;
      if (answer === undefined) {
        throw failed('Registration', response);
      }
      return answer;
    }
    case 429:
      return { status: 'tooManyAttempts' };
    default:
      throw failed('Registration', response);
  }
}

export async function verifyEmail(token: string): Promise<VerifyAnswer> {
  const { response } = await api.POST('/api/account/verify-email', { body: { token } });
  switch (response.status) {
    case 204:
      return { status: 'verified' };
    case 410:
      return { status: 'invalidLink' };
    default:
      throw failed('Email verification', response);
  }
}

export async function signIn(email: string, password: string): Promise<SignInAnswer> {
  const { data, error, response } = await api.POST('/api/account/login', { body: { email, password } });
  switch (response.status) {
    case 200: {
      const customer = toCustomer(data);
      if (customer === null) {
        throw failed('Sign-in', response);
      }
      return { status: 'signedIn', customer };
    }
    case 401:
      return { status: 'invalidCredentials' };
    case 403:
      return { status: 'emailNotVerified' };
    case 423: {
      const until = problemProperty(error, 'lockedUntil');
      if (typeof until !== 'string') {
        throw new Error('Locked answer without lockedUntil');
      }
      return { status: 'locked', until: new Date(until) };
    }
    case 429:
      return { status: 'tooManyAttempts' };
    default:
      throw failed('Sign-in', response);
  }
}

export async function signOut(): Promise<void> {
  const { response } = await api.POST('/api/account/logout');
  // 401: the session had already ended, which is what signing out wants.
  if (response.status !== 204 && response.status !== 401) {
    throw failed('Sign-out', response);
  }
}

/** The customer of this session, or null when nobody (or the administrator) is signed in. */
export async function fetchCustomer(signal?: AbortSignal): Promise<Customer | null> {
  const { data, response } = await api.GET('/api/account/me', { signal: signal ?? null });
  if (response.status === 401 || response.status === 403) {
    return null;
  }
  const customer = toCustomer(data);
  if (customer === null) {
    throw failed('Account check', response);
  }
  return customer;
}
