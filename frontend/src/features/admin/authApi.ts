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

/** What a wrong attempt of any step can answer (UC-04). */
type Refusal = { status: 'locked'; until: Date } | { status: 'tooManyAttempts' };

export type NextStep = 'SECOND_FACTOR' | 'ENROLLMENT';

export type SignInAnswer = { status: 'passwordAccepted'; nextStep: NextStep } | { status: 'invalidCredentials' } | Refusal;

/** The second factor or its enrollment (T-E1-16); {@code expired}: start again with the password. */
export type CodeAnswer =
  | { status: 'signedIn'; administrator: Administrator }
  | { status: 'invalidCode' }
  | { status: 'expired' }
  | Refusal;

export type EnrollmentAnswer =
  | { status: 'enrolled'; administrator: Administrator; recoveryCodes: string[] }
  | { status: 'invalidCode' }
  | { status: 'expired' }
  | Refusal;

export interface Enrollment {
  /** The secret to type by hand in the authenticator app. */
  secret: string;
  /** A PNG data URI of the QR code to scan. */
  qrCode: string;
}

function toAdministrator({ id, name }: AdministratorResponse): Administrator {
  if (id === undefined) {
    throw new Error('Incomplete administrator');
  }
  return { id, name: name ?? '' };
}

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

/** The answers every step shares: a lock with its end, or too many attempts from this IP. */
function refusal(response: Response, error: unknown): Refusal | null {
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
  return null;
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
    throw failed('Session check', response);
  }
  return { status: 'signedIn', administrator: toAdministrator(data) };
}

/** POST /api/admin/auth/login (UC-04): a right password only says which step comes next. */
export async function signIn(email: string, password: string): Promise<SignInAnswer> {
  const { data, error, response } = await api.POST('/api/admin/auth/login', { body: { email, password } });
  if (response.status === 401) {
    return { status: 'invalidCredentials' };
  }
  const refused = refusal(response, error);
  if (refused !== null) {
    return refused;
  }
  if (data?.nextStep === undefined) {
    throw failed('Sign-in', response);
  }
  return { status: 'passwordAccepted', nextStep: data.nextStep };
}

/** POST /api/admin/auth/second-factor (T-E1-16): a code of the app or a recovery code. */
export async function verifySecondFactor(code: string): Promise<CodeAnswer> {
  const { data, error, response } = await api.POST('/api/admin/auth/second-factor', { body: { code } });
  if (response.status === 401) {
    return { status: 'invalidCode' };
  }
  if (response.status === 409) {
    return { status: 'expired' };
  }
  const refused = refusal(response, error);
  if (refused !== null) {
    return refused;
  }
  if (data === undefined) {
    throw failed('Second factor', response);
  }
  return { status: 'signedIn', administrator: toAdministrator(data) };
}

/** GET /api/admin/auth/enrollment: a new secret each call; null when the pending sign-in expired. */
export async function startEnrollment(signal?: AbortSignal): Promise<Enrollment | null> {
  const { data, response } = await api.GET('/api/admin/auth/enrollment', { signal: signal ?? null });
  if (response.status === 409) {
    return null;
  }
  if (data?.secret === undefined || data.qrCode === undefined) {
    throw failed('Enrollment', response);
  }
  return { secret: data.secret, qrCode: data.qrCode };
}

/** POST /api/admin/auth/enrollment: a right code opens the back office and shows the recovery codes once. */
export async function confirmEnrollment(code: string): Promise<EnrollmentAnswer> {
  const { data, error, response } = await api.POST('/api/admin/auth/enrollment', { body: { code } });
  if (response.status === 401) {
    return { status: 'invalidCode' };
  }
  if (response.status === 409) {
    return { status: 'expired' };
  }
  const refused = refusal(response, error);
  if (refused !== null) {
    return refused;
  }
  if (data?.administrator === undefined) {
    throw failed('Enrollment', response);
  }
  return {
    status: 'enrolled',
    administrator: toAdministrator(data.administrator),
    recoveryCodes: data.recoveryCodes ?? [],
  };
}

export async function signOut(): Promise<void> {
  const { response } = await api.POST('/api/admin/auth/logout');
  if (!response.ok) {
    throw failed('Sign-out', response);
  }
}
