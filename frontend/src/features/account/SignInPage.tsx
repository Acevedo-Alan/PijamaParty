import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type SyntheticEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatTime } from '../../i18n/formatDate.ts';
import { CART_KEY } from '../cart/useCart.ts';
import { RECOVER_PATH, REGISTER_PATH } from './AccountLink.tsx';
import { signIn, type SignInAnswer } from './accountApi.ts';
import { AuthCard, AuthField, AuthSwitch, fieldErrorProps, PasswordInput, useMissingFields } from './AuthCard.tsx';
import { RETURN_KEY, returnPathOf } from './returnPath.ts';
import { SocialSignIn, socialMessage } from './SocialSignIn.tsx';
import { CUSTOMER_KEY } from './useCustomer.ts';

const messages = es.account.signIn;

/** The fields of the form, in the order the focus visits them. */
const SIGN_IN_FIELDS = ['email', 'password'] as const;

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
  // UC-27 (T-E4-14): where the return from Google or Facebook left the visitor.
  const [searchParams] = useSearchParams();
  const social = socialMessage(searchParams.get('social'));
  // T-E7-10: the checkout sends a guest here and expects it back.
  const returnPath = returnPathOf(searchParams.get(RETURN_KEY));
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const id = useId();
  const fields = useMissingFields<(typeof SIGN_IN_FIELDS)[number]>(id);
  const emailId = `${id}-email`;
  const passwordId = `${id}-password`;
  const attempt = useMutation({
    mutationFn: () => signIn(email, password),
    onSuccess: (answer) => {
      setPassword('');
      if (answer.status === 'signedIn') {
        queryClient.setQueryData(CUSTOMER_KEY, answer.customer);
        // Signed in, the header shows the account's cart, which the guest cart joined (T-E4-04).
        void queryClient.invalidateQueries({ queryKey: CART_KEY });
        void navigate(returnPath, { replace: true });
      }
    },
  });

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = { email: email.trim(), password };
    if (fields.check(SIGN_IN_FIELDS.filter((field) => values[field] === ''))) {
      attempt.mutate();
    }
  }

  const emailError = fields.isMissing('email') ? es.account.missing.email : null;
  const passwordError = fields.isMissing('password') ? es.account.missing.password : null;
  return (
    <AuthCard title={messages.title} intro={messages.intro}>
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
      {social !== null && (
        <p
          className={social.problem ? 'account-done account-error' : 'account-done'}
          role={social.problem ? 'alert' : 'status'}
        >
          {social.text}
        </p>
      )}
      <SocialSignIn />
      <form className="account-form" onSubmit={send} noValidate>
        <AuthField id={emailId} label={es.account.email} error={emailError}>
          <input
            id={emailId}
            name="email"
            type="email"
            inputMode="email"
            autoComplete="username"
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
        <AuthField
          id={passwordId}
          label={es.account.password}
          aside={
            <Link to={RECOVER_PATH} className="auth-aside-link">
              {messages.forgotPassword}
            </Link>
          }
          error={passwordError}
        >
          <PasswordInput
            id={passwordId}
            name="password"
            autoComplete="current-password"
            required
            value={password}
            {...fieldErrorProps(passwordId, passwordError)}
            onChange={(event) => {
              setPassword(event.target.value);
              fields.clear('password');
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
      <AuthSwitch question={messages.firstTime} to={REGISTER_PATH}>
        {es.account.registerLink}
      </AuthSwitch>
    </AuthCard>
  );
}
