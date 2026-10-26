import { useMutation } from '@tanstack/react-query';
import { useId, useState, type SyntheticEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { es } from '../../i18n/es.ts';
import { RECOVER_PATH, SIGN_IN_PATH } from './AccountLink.tsx';
import { resetPassword, type ResetAnswer } from './accountApi.ts';
import { AuthCard, AuthField, fieldErrorProps, PasswordInput, useMissingFields } from './AuthCard.tsx';
import type { SignInState } from './SignInPage.tsx';

const messages = es.account.reset;

function problemOf(answer: ResetAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return messages.error;
  }
  switch (answer?.status) {
    case 'passwordTooShort':
      return messages.passwordTooShort;
    case 'passwordTooLong':
      return messages.passwordTooLong;
    default:
      return null;
  }
}

/**
 * UC-19 (T-E4-03): the link of the reset email. The customer chooses a new password, which uses the link once; then
 * the sign-in page says the password changed. Resetting does not sign in.
 */
export function ResetPasswordPage() {
  const { hash } = useLocation();
  const navigate = useNavigate();
  const token = hash.replace(/^#/, '');
  const [password, setPassword] = useState('');
  const id = useId();
  const fields = useMissingFields<'password'>(id);
  const attempt = useMutation({
    mutationFn: () => resetPassword(token, password),
    onSuccess: (answer) => {
      if (answer.status === 'reset') {
        const state: SignInState = { passwordReset: true };
        void navigate(SIGN_IN_PATH, { replace: true, state });
      }
    },
  });

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    if (fields.check(password === '' ? ['password'] : [])) {
      attempt.mutate();
    }
  }

  const invalid = token === '' || attempt.data?.status === 'invalidLink';
  const passwordId = `${id}-password`;
  const hintId = `${id}-password-hint`;
  const passwordError = fields.isMissing('password') ? es.account.missing.password : null;
  return (
    <AuthCard title={messages.title} intro={invalid ? undefined : messages.intro}>
      {invalid ? (
        <div className="account-done" role="alert">
          <p>{messages.invalidLink}</p>
          <Link to={RECOVER_PATH} className="btn">
            {messages.newLink}
          </Link>
        </div>
      ) : (
        <form className="account-form" onSubmit={send} noValidate>
          <AuthField id={passwordId} label={messages.newPassword} error={passwordError}>
            <PasswordInput
              id={passwordId}
              name="password"
              autoComplete="new-password"
              required
              value={password}
              {...fieldErrorProps(passwordId, passwordError, hintId)}
              onChange={(event) => {
                setPassword(event.target.value);
                fields.clear('password');
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
            {problemOf(attempt.data, attempt.isError)}
          </p>
        </form>
      )}
    </AuthCard>
  );
}
