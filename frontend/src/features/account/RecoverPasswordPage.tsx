import { useMutation } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { SIGN_IN_PATH } from './AccountLink.tsx';
import { requestPasswordReset, type RecoveryAnswer } from './accountApi.ts';
import './account.css';

const messages = es.account.recover;

function problemOf(answer: RecoveryAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return messages.error;
  }
  return answer?.status === 'tooManyAttempts' ? es.account.tooManyAttempts : null;
}

/**
 * UC-19 (T-E4-03): someone who forgot its password asks for a reset link. The page always says the same, whether
 * the email has an account or not.
 */
export function RecoverPasswordPage() {
  const [email, setEmail] = useState('');
  const attempt = useMutation({ mutationFn: requestPasswordReset });
  const done = useRef<HTMLDivElement>(null);
  const id = useId();
  const requested = attempt.data?.status === 'requested';

  useEffect(() => {
    if (requested) {
      done.current?.focus();
    }
  }, [requested]);

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    attempt.mutate(email);
  }

  return (
    <main className="wrap account-page">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      {requested ? (
        <div className="account-done" ref={done} tabIndex={-1} role="status">
          <p>{messages.done(email.trim())}</p>
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
          <p className="account-switch">
            <Link to={SIGN_IN_PATH}>{messages.backToSignIn}</Link>
          </p>
        </>
      )}
    </main>
  );
}
