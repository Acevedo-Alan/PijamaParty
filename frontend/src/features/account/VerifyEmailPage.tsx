import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { es } from '../../i18n/es.ts';
import { SIGN_IN_PATH } from './AccountLink.tsx';
import { verifyEmail } from './accountApi.ts';
import type { SignInState } from './SignInPage.tsx';
import './account.css';

const messages = es.account.verify;

/** The page of the verification link; the token comes in the fragment, which never reaches a server. */
export const VERIFY_PATH = '/cuenta/verificar';

/**
 * UC-19 (T-E4-02): the link of the verification email. The token is used once, as soon as the page opens; then the
 * sign-in page says the email is verified. Verifying does not sign in.
 */
export function VerifyEmailPage() {
  const { hash } = useLocation();
  const navigate = useNavigate();
  const token = hash.replace(/^#/, '');
  const verification = useMutation({
    mutationFn: verifyEmail,
    onSuccess: (answer) => {
      if (answer.status === 'verified') {
        const state: SignInState = { verified: true };
        void navigate(SIGN_IN_PATH, { replace: true, state });
      }
    },
  });
  // A token is used once: the second run of the effect in development must not open it again.
  const opened = useRef(false);

  const { mutate } = verification;

  useEffect(() => {
    if (token !== '' && !opened.current) {
      opened.current = true;
      mutate(token);
    }
  }, [token, mutate]);

  const invalid = token === '' || verification.data?.status === 'invalidLink';
  return (
    <main className="wrap account-page">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      {invalid || verification.isError ? (
        <div className="account-done" role="alert">
          <p>{verification.isError ? messages.error : messages.invalidLink}</p>
          <Link to={SIGN_IN_PATH} className="btn">
            {messages.signIn}
          </Link>
        </div>
      ) : (
        <p role="status">{messages.verifying}</p>
      )}
    </main>
  );
}
