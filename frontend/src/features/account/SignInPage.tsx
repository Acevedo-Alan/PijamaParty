import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type SyntheticEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatTime } from '../../i18n/formatDate.ts';
import { CART_KEY } from '../cart/useCart.ts';
import { RECOVER_PATH, REGISTER_PATH } from './AccountLink.tsx';
import { signIn, type SignInAnswer } from './accountApi.ts';
import { CUSTOMER_KEY } from './useCustomer.ts';
import './account.css';

const messages = es.account.signIn;

/** What the verification and reset pages leave in the navigation state: the email was just verified, or the password reset. */
export interface SignInState {
  verified?: boolean;
  passwordReset?: boolean;
}

function problemOf(answer: SignInAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return messages.error;
  }
  switch (answer?.status) {
    case 'invalidCredentials':
      return messages.invalidCredentials;
    case 'emailNotVerified':
      return messages.emailNotVerified;
    case 'locked':
      return messages.locked(formatTime(answer.until));
    case 'tooManyAttempts':
      return es.account.tooManyAttempts;
    default:
      return null;
  }
}

/** UC-19 (T-E4-02): the customer signs in with email and password, once its email is verified. */
export function SignInPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const state = useLocation().state as SignInState | null;
  const verified = state?.verified === true;
  const passwordReset = state?.passwordReset === true;
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const id = useId();
  const attempt = useMutation({
    mutationFn: () => signIn(email, password),
    onSuccess: (answer) => {
      setPassword('');
      if (answer.status === 'signedIn') {
        queryClient.setQueryData(CUSTOMER_KEY, answer.customer);
        // Signed in, the header shows the account's cart.
        void queryClient.invalidateQueries({ queryKey: CART_KEY });
        void navigate('/', { replace: true });
      }
    },
  });

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    attempt.mutate();
  }

  const incomplete = email.trim() === '' || password === '';
  return (
    <main className="wrap account-page">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      {verified && (
        <p className="account-done" role="status">
          {messages.verified}
        </p>
      )}
      {passwordReset && (
        <p className="account-done" role="status">
          {messages.passwordReset}
        </p>
      )}
      <form className="account-form" onSubmit={send} noValidate>
        <label htmlFor={`${id}-email`}>{es.account.email}</label>
        <input
          id={`${id}-email`}
          name="email"
          type="email"
          inputMode="email"
          autoComplete="username"
          maxLength={254}
          required
          value={email}
          onChange={(event) => {
            setEmail(event.target.value);
          }}
        />
        <label htmlFor={`${id}-password`}>{es.account.password}</label>
        <input
          id={`${id}-password`}
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => {
            setPassword(event.target.value);
          }}
        />
        <button type="submit" className="btn" disabled={attempt.isPending || incomplete}>
          {attempt.isPending ? messages.sending : messages.send}
        </button>
        <p className="account-error" role="alert">
          {problemOf(attempt.data, attempt.isError)}
        </p>
      </form>
      <p className="account-switch">
        <Link to={RECOVER_PATH}>{messages.forgotPassword}</Link>
      </p>
      <p className="account-switch">
        {messages.noAccount} <Link to={REGISTER_PATH}>{messages.createAccount}</Link>
      </p>
    </main>
  );
}
