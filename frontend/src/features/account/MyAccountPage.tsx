import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react';
import { Navigate, useSearchParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { SIGN_IN_PATH } from './AccountLink.tsx';
import { signInWithProviderUrl, type SignInProvider } from './accountApi.ts';
import {
  SIGN_IN_METHODS_KEY,
  createPassword,
  fetchSignInMethods,
  requestLink,
  unlinkProvider,
  type CreatePasswordAnswer,
  type LinkedIdentity,
} from './signInMethodsApi.ts';
import { goToProvider } from './providerRedirect.ts';
import { PROVIDER_NAMES, useSignInProviders } from './SocialSignIn.tsx';
import { CUSTOMER_KEY, useCustomer } from './useCustomer.ts';
import './account.css';

const messages = es.account.myAccount;

/** The providers in the order the page lists them. */
const PROVIDERS: readonly SignInProvider[] = ['GOOGLE', 'FACEBOOK'];

/** Where the provider's return of a link left the browser: the `social` value of this page's query. */
function socialNotice(social: string | null): { text: string; problem: boolean } | null {
  switch (social) {
    case 'linked':
      return { text: messages.social.linked, problem: false };
    case 'cancelled':
      return { text: messages.social.cancelled, problem: false };
    case 'already-linked':
      return { text: messages.social.alreadyLinked, problem: true };
    case 'failed':
      return { text: messages.social.failed, problem: true };
    default:
      return null;
  }
}

/**
 * UC-27 (T-E4-15): "Mi cuenta" at /cuenta. It shows how the customer signs in: its password, or "Crear contraseña"
 * while it has none, and each provider, linked ("Desvincular", after a confirmation) or not ("Vincular", only for the
 * enabled providers). The last way in cannot be unlinked: the page says so and offers to create a password. A visitor
 * who is not signed in goes to sign in.
 */
export function MyAccountPage() {
  const customer = useCustomer();
  const queryClient = useQueryClient();
  const enabled = useSignInProviders();
  const [searchParams] = useSearchParams();
  const methods = useQuery({
    queryKey: SIGN_IN_METHODS_KEY,
    queryFn: ({ signal }) => fetchSignInMethods(signal),
    enabled: customer !== undefined && customer !== null,
  });
  const [confirming, setConfirming] = useState<SignInProvider | null>(null);
  const [creatingPassword, setCreatingPassword] = useState(false);
  const [notice, setNotice] = useState<{
    text: string;
    problem: boolean;
  } | null>(() => socialNotice(searchParams.get('social')));
  const noticeRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (notice !== null) {
      noticeRef.current?.focus();
    }
  }, [notice]);

  function signedOut() {
    queryClient.setQueryData(CUSTOMER_KEY, null);
  }

  function done(text: string, problem = false) {
    setConfirming(null);
    setNotice({ text, problem });
    void queryClient.invalidateQueries({ queryKey: SIGN_IN_METHODS_KEY });
  }

  const unlink = useMutation({
    mutationFn: unlinkProvider,
    onSuccess: (answer, provider) => {
      const name = PROVIDER_NAMES[provider];
      switch (answer.status) {
        case 'unlinked':
          done(messages.unlinked(name));
          break;
        case 'notLinked':
          done(messages.notLinkedAnymore);
          break;
        case 'lastSignInMethod':
          done(messages.lastSignInMethod(name), true);
          setCreatingPassword(true);
          break;
        case 'signedOut':
          signedOut();
          break;
      }
    },
    onError: () => {
      setNotice({ text: messages.unlinkError, problem: true });
    },
  });

  const link = useMutation({
    mutationFn: requestLink,
    onSuccess: (answer, provider) => {
      switch (answer.status) {
        case 'requested':
          // A whole-page navigation to the provider, which comes back here.
          goToProvider(signInWithProviderUrl(provider));
          break;
        case 'notEnabled':
          setNotice({ text: messages.linkNotEnabled, problem: true });
          break;
        case 'signedOut':
          signedOut();
          break;
      }
    },
    onError: () => {
      setNotice({ text: messages.linkError, problem: true });
    },
  });

  if (customer === null || methods.data === null) {
    return <Navigate to={SIGN_IN_PATH} replace />;
  }

  const current = methods.data;
  const linked = (provider: SignInProvider): LinkedIdentity | undefined =>
    current?.identities.find((identity) => identity.provider === provider);
  // A provider no longer enabled still shows while linked, so it can be unlinked.
  const shown = PROVIDERS.filter((provider) => enabled.includes(provider) || linked(provider) !== undefined);
  const busy = unlink.isPending || link.isPending;

  return (
    <main className="wrap account-page my-account">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      {customer !== undefined && (
        <p className="account-intro">
          {customer.name}
          <br />
          {customer.email}
        </p>
      )}
      <p
        className={notice?.problem === true ? 'address-notice account-error' : 'address-notice'}
        ref={noticeRef}
        tabIndex={-1}
        role={notice?.problem === true ? 'alert' : 'status'}
      >
        {notice?.text}
      </p>
      <h2 className="my-account-heading">{messages.methodsTitle}</h2>
      <p className="account-intro">{messages.methodsIntro}</p>
      {methods.isError && (
        <div className="address-load-error" role="alert">
          <p>{messages.loadError}</p>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              void methods.refetch();
            }}
          >
            {messages.retry}
          </button>
        </div>
      )}
      {current === undefined ? (
        !methods.isError && <p className="account-intro">{messages.loading}</p>
      ) : (
        <ul className="address-list">
          <li className="address-card">
            <p className="address-line">{messages.password}</p>
            <p className="address-place">{current.hasPassword ? messages.passwordSet : messages.passwordMissing}</p>
            {!current.hasPassword &&
              (creatingPassword ? (
                <PasswordForm
                  onCancel={() => {
                    setCreatingPassword(false);
                  }}
                  onDone={(text) => {
                    setCreatingPassword(false);
                    done(text);
                  }}
                  onSignedOut={signedOut}
                />
              ) : (
                <div className="address-actions">
                  <button
                    type="button"
                    className="address-action"
                    onClick={() => {
                      setNotice(null);
                      setCreatingPassword(true);
                    }}
                  >
                    {messages.createPassword}
                  </button>
                </div>
              ))}
          </li>
          {shown.map((provider) => {
            const identity = linked(provider);
            const name = PROVIDER_NAMES[provider];
            return (
              <li key={provider} className="address-card">
                <p className="address-line">{name}</p>
                <p className="address-place">
                  {identity === undefined ? messages.notLinked : messages.linkedAs(identity.email)}
                </p>
                {identity === undefined ? (
                  <div className="address-actions">
                    <button
                      type="button"
                      className="address-action"
                      aria-label={messages.linkProvider(name)}
                      disabled={busy}
                      onClick={() => {
                        setNotice(null);
                        link.mutate(provider);
                      }}
                    >
                      {messages.link}
                    </button>
                  </div>
                ) : confirming === provider ? (
                  <div className="address-confirm" role="group" aria-label={messages.unlinkProvider(name)}>
                    <p>{messages.confirmUnlink(name)}</p>
                    <div className="address-actions">
                      <button
                        type="button"
                        className="btn"
                        disabled={busy}
                        onClick={() => {
                          unlink.mutate(provider);
                        }}
                      >
                        {messages.confirmUnlinkYes}
                      </button>
                      <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => {
                          setConfirming(null);
                        }}
                      >
                        {messages.cancel}
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="address-actions">
                    <button
                      type="button"
                      className="address-action"
                      aria-label={messages.unlinkProvider(name)}
                      disabled={busy}
                      onClick={() => {
                        setNotice(null);
                        setConfirming(provider);
                      }}
                    >
                      {messages.unlink}
                    </button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}

function passwordProblem(answer: CreatePasswordAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return messages.passwordError;
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

interface PasswordFormProps {
  onCancel: () => void;
  onDone: (message: string) => void;
  onSignedOut: () => void;
}

/** "Crear contraseña": only for an account without one; the session is enough. */
function PasswordForm({ onCancel, onDone, onSignedOut }: PasswordFormProps) {
  const [password, setPassword] = useState('');
  const input = useRef<HTMLInputElement>(null);
  const id = useId();
  const save = useMutation({
    mutationFn: () => createPassword(password),
    onSuccess: (answer) => {
      switch (answer.status) {
        case 'created':
          onDone(messages.passwordCreated);
          break;
        case 'alreadySet':
          onDone(messages.passwordAlreadySet);
          break;
        case 'signedOut':
          onSignedOut();
          break;
        case 'passwordTooShort':
        case 'passwordTooLong':
          input.current?.focus();
          break;
      }
    },
  });

  useEffect(() => {
    input.current?.focus();
  }, []);

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    save.mutate();
  }

  return (
    <form className="account-form my-account-password" onSubmit={send} noValidate>
      <label htmlFor={`${id}-password`}>{messages.newPassword}</label>
      <input
        id={`${id}-password`}
        ref={input}
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
      <div className="address-actions">
        <button type="submit" className="btn" disabled={save.isPending || password === ''}>
          {save.isPending ? messages.savingPassword : messages.savePassword}
        </button>
        <button type="button" className="btn btn-secondary" onClick={onCancel}>
          {messages.cancel}
        </button>
      </div>
      <p className="account-error" role="alert">
        {passwordProblem(save.data, save.isError)}
      </p>
    </form>
  );
}
