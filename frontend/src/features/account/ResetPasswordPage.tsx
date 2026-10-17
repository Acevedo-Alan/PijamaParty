import { useMutation } from '@tanstack/react-query';
import { useId, useState, type SyntheticEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { es } from '../../i18n/es.ts';
import { RECOVER_PATH, SIGN_IN_PATH } from './AccountLink.tsx';
import { resetPassword, type ResetAnswer } from './accountApi.ts';
import type { SignInState } from './SignInPage.tsx';
import './account.css';

const messages = es.account.reset;

/** The page of the reset link; the token comes in the fragment, which never reaches a server. */
export const RESET_PATH = '/cuenta/restablecer';

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
    attempt.mutate();
  }

  const invalid = token === '' || attempt.data?.status === 'invalidLink';
  return (
    <main className="wrap account-page">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      {invalid ? (
        <div className="account-done" role="alert">
          <p>{messages.invalidLink}</p>
          <Link to={RECOVER_PATH} className="btn">
            {messages.newLink}
          </Link>
        </div>
      ) : (
        <form className="account-form" onSubmit={send} noValidate>
          <label htmlFor={`${id}-password`}>{messages.newPassword}</label>
          <input
            id={`${id}-password`}
            name="password"
            type="password"
            autoComplete="new-password"
            aria-describedby={`${id}-password-hint`}
            required
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
            }}
          />
          <p id={`${id}-password-hint`} className="account-hint">
            {messages.passwordHint}
          </p>
          <button type="submit" className="btn" disabled={attempt.isPending || password === ''}>
            {attempt.isPending ? messages.sending : messages.send}
          </button>
          <p className="account-error" role="alert">
            {problemOf(attempt.data, attempt.isError)}
          </p>
        </form>
      )}
    </main>
  );
}
