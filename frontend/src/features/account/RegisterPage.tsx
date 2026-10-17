import { useMutation } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { SIGN_IN_PATH } from './AccountLink.tsx';
import { register, type RegisterAnswer } from './accountApi.ts';
import { SocialSignIn } from './SocialSignIn.tsx';
import './account.css';

const messages = es.account.register;

interface RegisterForm {
  name: string;
  email: string;
  password: string;
}

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
  const registered = attempt.data?.status === 'registered';

  useEffect(() => {
    if (registered) {
      done.current?.focus();
    }
  }, [registered]);

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    attempt.mutate(form);
  }

  function change(field: keyof RegisterForm, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  const incomplete = form.name.trim() === '' || form.email.trim() === '' || form.password === '';
  return (
    <main className="wrap account-page">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      {registered ? (
        <div className="account-done" ref={done} tabIndex={-1} role="status">
          <p>{messages.done(form.email.trim())}</p>
        </div>
      ) : (
        <>
          <p className="account-intro">{messages.intro}</p>
          <SocialSignIn />
          <form className="account-form" onSubmit={send} noValidate>
            <label htmlFor={`${id}-name`}>{messages.name}</label>
            <input
              id={`${id}-name`}
              name="name"
              autoComplete="name"
              maxLength={100}
              required
              value={form.name}
              onChange={(event) => {
                change('name', event.target.value);
              }}
            />
            <label htmlFor={`${id}-email`}>{es.account.email}</label>
            <input
              id={`${id}-email`}
              name="email"
              type="email"
              inputMode="email"
              autoComplete="email"
              maxLength={254}
              required
              value={form.email}
              onChange={(event) => {
                change('email', event.target.value);
              }}
            />
            <label htmlFor={`${id}-password`}>{es.account.password}</label>
            <input
              id={`${id}-password`}
              name="password"
              type="password"
              autoComplete="new-password"
              aria-describedby={`${id}-password-hint`}
              required
              value={form.password}
              onChange={(event) => {
                change('password', event.target.value);
              }}
            />
            <p id={`${id}-password-hint`} className="account-hint">
              {messages.passwordHint}
            </p>
            <button type="submit" className="btn" disabled={attempt.isPending || incomplete}>
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
          <p className="account-switch">
            {messages.haveAccount} <Link to={SIGN_IN_PATH}>{es.account.signIn.title}</Link>
          </p>
        </>
      )}
    </main>
  );
}
