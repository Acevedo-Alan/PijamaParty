import { useMutation } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { SIGN_IN_PATH } from './AccountLink.tsx';
import { requestPasswordReset, type RecoveryAnswer } from './accountApi.ts';
import { AuthCard, AuthField, AuthSwitch, fieldErrorProps, useMissingFields } from './AuthCard.tsx';

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
  const fields = useMissingFields<'email'>(id);
  const requested = attempt.data?.status === 'requested';

  useEffect(() => {
    if (requested) {
      done.current?.focus();
    }
  }, [requested]);

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    if (fields.check(email.trim() === '' ? ['email'] : [])) {
      attempt.mutate(email);
    }
  }

  const emailId = `${id}-email`;
  const emailError = fields.isMissing('email') ? es.account.missing.email : null;
  return (
    <AuthCard title={messages.title} intro={requested ? undefined : messages.intro}>
      {requested ? (
        <div className="account-done" ref={done} tabIndex={-1} role="status">
          <p>{messages.done(email.trim())}</p>
          <Link to={SIGN_IN_PATH} className="btn">
            {messages.backToSignIn}
          </Link>
        </div>
      ) : (
        <>
          <form className="account-form" onSubmit={send} noValidate>
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
                value={email}
                {...fieldErrorProps(emailId, emailError)}
                onChange={(event) => {
                  setEmail(event.target.value);
                  fields.clear('email');
                }}
              />
            </AuthField>
            <button type="submit" className="btn" disabled={attempt.isPending}>
              {attempt.isPending ? messages.sending : messages.send}
            </button>
            <p className="account-error" role="alert">
              {problemOf(attempt.data, attempt.isError)}
            </p>
          </form>
          <AuthSwitch question={messages.remembered} to={SIGN_IN_PATH}>
            {messages.backToSignIn}
          </AuthSwitch>
        </>
      )}
    </AuthCard>
  );
}
