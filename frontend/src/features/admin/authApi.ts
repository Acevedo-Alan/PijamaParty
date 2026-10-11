import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { problemProperty } from './adminSession.ts';

type AdministratorResponse = components['schemas']['AdministratorResponse'];

export interface Administrator {
  id: string;
  name: string;
}

/** GET /api/admin/auth/me. It also leaves the CSRF cookie that the sign-in POST needs. */
export type SessionAnswer =
  | { status: 'signedIn'; administrator: Administrator }
  | { status: 'signedOut' }
  | { status: 'forbidden' };

export type SignInAnswer =
  | { status: 'signedIn'; administrator: Administrator }
  | { status: 'invalidCredentials' }
  | { status: 'locked'; until: Date }
  | { status: 'tooManyAttempts' };

function toAdministrator({ id, name }: AdministratorResponse): Administrator {
  if (id === undefined) {
    throw new Error('Incomplete administrator');
  }
  return { id, name: name ?? '' };
}

export async function fetchSession(signal?: AbortSignal): Promise<SessionAnswer> {
  const { data, response } = await api.GET('/api/admin/auth/me', { signal: signal ?? null });
  if (response.status === 401) {
    return { status: 'signedOut' };
  }
  if (response.status === 403) {
    return { status: 'forbidden' };
  }
  if (data === undefined) {
    throw new Error(`Session check failed with status ${String(response.status)}`);
  }
  return { status: 'signedIn', administrator: toAdministrator(data) };
}

/** POST /api/admin/auth/login (UC-04). Wrong credentials never say which field failed. */
export async function signIn(email: string, password: string): Promise<SignInAnswer> {
  const { data, error, response } = await api.POST('/api/admin/auth/login', { body: { email, password } });
  if (response.status === 401) {
    return { status: 'invalidCredentials' };
  }
  if (response.status === 423) {
    const until = problemProperty(error, 'lockedUntil');
    if (typeof until !== 'string') {
      throw new Error('Locked answer without lockedUntil');
    }
    return { status: 'locked', until: new Date(until) };
  }
  if (response.status === 429) {
    return { status: 'tooManyAttempts' };
  }
  if (data === undefined) {
    throw new Error(`Sign-in failed with status ${String(response.status)}`);
  }
  return { status: 'signedIn', administrator: toAdministrator(data) };
}

export async function signOut(): Promise<void> {
  const { response } = await api.POST('/api/admin/auth/logout');
  if (!response.ok) {
    throw new Error(`Sign-out failed with status ${String(response.status)}`);
  }
}
