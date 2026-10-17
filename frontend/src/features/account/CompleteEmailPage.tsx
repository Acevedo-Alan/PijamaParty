import { useMutation } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { SIGN_IN_PATH } from './AccountLink.tsx';
import { giveExternalAccountEmail, type ExternalEmailAnswer } from './accountApi.ts';
import './account.css';

const messages = es.account.completeEmail;

/** Where the provider's return sends a visitor whose Facebook account gave no email. */
export const COMPLETE_EMAIL_PATH = '/cuenta/completar-email';

function problemOf(answer: ExternalEmailAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return messages.error;
  }
  switch (answer?.status) {
    case 'invalidEmail':
      return messages.invalidEmail;
    case 'tooManyAttempts':
      return es.account.tooManyAttempts;
    default:
      return null;
  }
}

/**
 * UC-27 (T-E4-14): a Facebook account came back without an email, so the visitor gives one. The answer is the same
 * whether an account has it or not; the link of the email confirms it before anything is linked or signed in.
 */
export function CompleteEmailPage() {
  const [email, setEmail] = useState('');
  const attempt = useMutation({ mutationFn: giveExternalAccountEmail });
  const done = useRef<HTMLDivElement>(null);
  const id = useId();
  const status = attempt.data?.status;

  useEffect(() => {
    if (status === 'requested' || status === 'expired') {
      done.current?.focus();
    }
  }, [status]);

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    attempt.mutate(email);
  }

  return (
    <main className="wrap account-page">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      {status === 'requested' || status === 'expired' ? (
        <div className="account-done" ref={done} tabIndex={-1} role="status">
          <p>{status === 'requested' ? messages.done(email.trim()) : messages.expired}</p>
          <Link to={SIGN_IN_PATH} className="btn">
            {messages.backToSignIn}
          </Link>
        </div>
      ) : (
        <>
          <p className="account-intro">{messages.intro}</p>
          <form className="account-form" onSubmit={send} noValidate>
            <label htmlFor={`${id}-email`}>{es.account.email}</label>
            <input
              id={`${id}-email`}
              name="email"
              type="email"
              inputMode="email"
              autoComplete="email"
              maxLength={254}
              required
              value={email}
              onChange={(event) => {
                setEmail(event.target.value);
              }}
            />
            <button type="submit" className="btn" disabled={attempt.isPending || email.trim() === ''}>
              {attempt.isPending ? messages.sending : messages.send}
            </button>
            <p className="account-error" role="alert">
              {problemOf(attempt.data, attempt.isError)}
            </p>
          </form>
        </>
      )}
    </main>
  );
}
