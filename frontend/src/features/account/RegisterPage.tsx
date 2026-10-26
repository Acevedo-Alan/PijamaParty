import { useMutation } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { SIGN_IN_PATH } from './AccountLink.tsx';
import { register, type RegisterAnswer } from './accountApi.ts';
import { AuthCard, AuthField, AuthSwitch, fieldErrorProps, PasswordInput, useMissingFields } from './AuthCard.tsx';
import { SocialSignIn } from './SocialSignIn.tsx';

const messages = es.account.register;

interface RegisterForm {
  name: string;
  email: string;
  password: string;
}

/** The fields of the form, in the order the focus visits them. */
const REGISTER_FIELDS = ['name', 'email', 'password'] as const;

function problemOf(answer: RegisterAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return messages.error;
  }
  switch (answer?.status) {
    case 'invalidEmail':
      return messages.invalidEmail;
    case 'invalidName':
      return messages.invalidName;
    case 'passwordTooShort':
      return messages.passwordTooShort;
    case 'passwordTooLong':
      return messages.passwordTooLong;
    case 'tooManyAttempts':
      return es.account.tooManyAttempts;
    default:
      return null;
  }
}

/**
 * UC-19 (T-E4-02): registration with name, email and password. The account is created unverified and the page tells
 * where the verification link went; it does not sign in.
 */
export function RegisterPage() {
  const attempt = useMutation({
    mutationFn: ({ email, password, name }: RegisterForm) => register(email, password, name),
  });
  const [form, setForm] = useState<RegisterForm>({ name: '', email: '', password: '' });
  const done = useRef<HTMLDivElement>(null);
  const id = useId();
  const fields = useMissingFields<keyof RegisterForm>(id);
  const registered = attempt.data?.status === 'registered';

  useEffect(() => {
    if (registered) {
      done.current?.focus();
    }
  }, [registered]);

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = { name: form.name.trim(), email: form.email.trim(), password: form.password };
    if (fields.check(REGISTER_FIELDS.filter((field) => values[field] === ''))) {
      attempt.mutate(form);
    }
  }

  function change(field: keyof RegisterForm, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    fields.clear(field);
  }

  const nameId = `${id}-name`;
  const emailId = `${id}-email`;
  const passwordId = `${id}-password`;
  const hintId = `${id}-password-hint`;
  const nameError = fields.isMissing('name') ? es.account.missing.name : null;
  const emailError = fields.isMissing('email') ? es.account.missing.email : null;
  const passwordError = fields.isMissing('password') ? es.account.missing.password : null;
  return (
    <AuthCard title={messages.title} intro={registered ? undefined : messages.intro}>
      {registered ? (
        <div className="account-done" ref={done} tabIndex={-1} role="status">
          <p>{messages.done(form.email.trim())}</p>
        </div>
      ) : (
        <>
          <SocialSignIn />
          <form className="account-form" onSubmit={send} noValidate>
            <AuthField id={nameId} label={messages.name} error={nameError}>
              <input
                id={nameId}
                name="name"
                autoComplete="name"
                maxLength={100}
                required
                value={form.name}
                {...fieldErrorProps(nameId, nameError)}
                onChange={(event) => {
                  change('name', event.target.value);
                }}
              />
            </AuthField>
            <AuthField id={emailId} label={es.account.email} error={emailError}>
              <input
                id={emailId}
                name="email"
                type="email"
                inputMode="email"
                autoComplete="email"
                autoCapitalize="none"
                spellCheck={false}
                maxLength={254}
                required
                value={form.email}
                {...fieldErrorProps(emailId, emailError)}
                onChange={(event) => {
                  change('email', event.target.value);
                }}
              />
            </AuthField>
            <AuthField id={passwordId} label={es.account.password} error={passwordError}>
              <PasswordInput
                id={passwordId}
                name="password"
                autoComplete="new-password"
                required
                value={form.password}
                {...fieldErrorProps(passwordId, passwordError, hintId)}
                onChange={(event) => {
                  change('password', event.target.value);
                }}
              />
              <p id={hintId} className="account-hint">
                {messages.passwordHint}
              </p>
            </AuthField>
            <button type="submit" className="btn" disabled={attempt.isPending}>
              {attempt.isPending ? messages.sending : messages.send}
            </button>
            <p className="account-error" role="alert">
              {attempt.data?.status === 'emailTaken' ? (
                <>
                  {messages.emailTaken} <Link to={SIGN_IN_PATH}>{messages.emailTakenAction}</Link>
                </>
              ) : (
                problemOf(attempt.data, attempt.isError)
              )}
            </p>
          </form>
          <AuthSwitch question={messages.haveAccount} to={SIGN_IN_PATH}>
            {es.account.signIn.title}
          </AuthSwitch>
        </>
      )}
    </AuthCard>
  );
}
