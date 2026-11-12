import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react';
import { Navigate, useSearchParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import type { AccountDeletedState } from './AccountDeletedNotice.tsx';
import { AccountDataSection } from './AccountDataSection.tsx';
import { SIGN_IN_PATH } from './AccountLink.tsx';
import { Collapsible } from '../../components/Collapsible.tsx';
import { requestPasswordReset, signInWithProviderUrl, type SignInProvider } from './accountApi.ts';
import {
  SIGN_IN_METHODS_KEY,
  createPassword,
  fetchSignInMethods,
  requestLink,
  unlinkProvider,
  type CreatePasswordAnswer,
  type LinkedIdentity,
} from './signInMethodsApi.ts';
import { forgetAccount } from './forgetAccount.ts';
import { goToProvider } from './providerRedirect.ts';
import { PROVIDER_NAMES, useSignInProvidersState } from './SocialSignIn.tsx';
import { PersonalDataCard } from './PersonalDataCard.tsx';
import { initials } from './AccountArea.tsx';
import { Group } from './Group.tsx';
import { CUSTOMER_KEY, useCustomer } from './useCustomer.ts';
import './account.css';

const messages = es.account.myAccount;

/** The providers in the order the page lists them. */
const PROVIDERS: readonly SignInProvider[] = ['GOOGLE', 'FACEBOOK'];

/** How long "Cambiar contraseña por email" waits after sending before it can send again (T-E7-63). */
const RESEND_WAIT_SECONDS = 60;

/** "0:59". */
function waitText(seconds: number): string {
  return `${String(Math.floor(seconds / 60))}:${String(seconds % 60).padStart(2, '0')}`;
}

/** The seconds left of a wait the page starts, counting down once a second; 0 when there is none. */
function useCountdown(seconds: number): [number, () => void] {
  const [left, setLeft] = useState(0);
  const running = left > 0;
  useEffect(() => {
    if (!running) {
      return undefined;
    }
    const timer = setInterval(() => {
      setLeft((value) => Math.max(0, value - 1));
    }, 1000);
    return () => {
      clearInterval(timer);
    };
  }, [running]);
  return [
    left,
    () => {
      setLeft(seconds);
    },
  ];
}

/** Where the provider's return of a link or a confirmation left the browser: the `social` value of this page's query. */
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
    case 'reauthenticated':
      return { text: messages.social.reauthenticated, problem: false };
    case 'reauthentication-cancelled':
      return { text: messages.social.reauthenticationCancelled, problem: false };
    case 'reauthentication-failed':
      return { text: messages.social.reauthenticationFailed, problem: true };
    default:
      return null;
  }
}

/**
 * "Datos y acceso" at /cuenta/datos (T-E7-62; "Mi cuenta" at /cuenta before it). Its personal data, read only. UC-27
 * (T-E4-15): how the customer signs in: its password, with "Cambiar contraseña" (the UC-19 recovery email to its own
 * email), or "Crear contraseña" while it has none, and each provider, linked ("Desvincular", after a confirmation) or
 * not ("Vincular", only for the enabled providers). The last way in cannot be unlinked: the page says so and offers to
 * create a password. UC-28 (T-E4-16): "Privacidad", folded at the end, downloads the customer's data and deletes its
 * account. A visitor who is not signed in goes to sign in.
 */
export function MyAccountPage() {
  const customer = useCustomer();
  const queryClient = useQueryClient();
  const { providers: enabled, known: providersKnown } = useSignInProvidersState();
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
  const [deleted, setDeleted] = useState(false);

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

  const [resendIn, startResendWait] = useCountdown(RESEND_WAIT_SECONDS);

  const changePassword = useMutation({
    mutationFn: requestPasswordReset,
    onSuccess: (answer, email) => {
      if (answer.status === 'requested') {
        startResendWait();
        setNotice({ text: messages.changePasswordSent(email), problem: false });
      } else {
        setNotice({ text: es.account.tooManyAttempts, problem: true });
      }
    },
    onError: () => {
      setNotice({ text: messages.changePasswordError, problem: true });
    },
  });

  if (deleted) {
    // Before the signed-out check: the deletion forgets the customer, and the page goes home, not to sign in.
    const state: AccountDeletedState = { accountDeleted: true };
    return <Navigate to="/" replace state={state} />;
  }
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
    <div className="account-page my-account">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      {customer !== undefined && (
        // T-E7-63: who the page is about, before its groups.
        <div className="account-profile">
          <span className="account-profile-initials" aria-hidden="true">
            {initials(customer.name)}
          </span>
          <p className="account-profile-name">{customer.name}</p>
          <p className="account-profile-email">{customer.email}</p>
        </div>
      )}
      {customer !== undefined && <PersonalDataCard customer={customer} onSignedOut={signedOut} />}
      <Group.Section title={messages.methodsTitle}>
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
          <Group.Card>
            <Group.Field label={messages.password}>
              {current.hasPassword ? messages.passwordSet : <Group.Missing>{messages.passwordMissing}</Group.Missing>}
            </Group.Field>
            {current.hasPassword &&
              customer !== undefined &&
              (resendIn > 0 ? (
                // T-E7-63: sent; it waits a minute before it can be sent again, so it is not pressed again in doubt.
                <Group.Done>{messages.passwordLinkSent(waitText(resendIn))}</Group.Done>
              ) : (
                // T-E7-62: the UC-19 recovery email, to the account's own email; the link chooses the new password.
                <Group.ActionButton
                  icon="mail"
                  disabled={changePassword.isPending}
                  onClick={() => {
                    setNotice(null);
                    changePassword.mutate(customer.email);
                  }}
                >
                  {changePassword.isPending ? messages.changingPassword : messages.changePassword}
                </Group.ActionButton>
              ))}
            {!current.hasPassword &&
              (creatingPassword ? (
                <Group.Row>
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
                </Group.Row>
              ) : (
                <Group.ActionButton
                  icon="key"
                  onClick={() => {
                    setNotice(null);
                    setCreatingPassword(true);
                  }}
                >
                  {messages.createPassword}
                </Group.ActionButton>
              ))}
            {shown.map((provider) => {
              const identity = linked(provider);
              const name = PROVIDER_NAMES[provider];
              return (
                <Group.Rows key={provider} label={name}>
                  <Group.Field label={name}>
                    {identity === undefined ? (
                      <Group.Missing>{messages.notLinked}</Group.Missing>
                    ) : (
                      messages.linkedAs(identity.email)
                    )}
                  </Group.Field>
                  {identity === undefined ? (
                    <Group.ActionButton
                      icon="link"
                      disabled={busy}
                      onClick={() => {
                        setNotice(null);
                        link.mutate(provider);
                      }}
                    >
                      {messages.linkProvider(name)}
                    </Group.ActionButton>
                  ) : confirming === provider ? (
                    <Group.Row>
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
                    </Group.Row>
                  ) : (
                    <Group.ActionButton
                      icon="unlink"
                      disabled={busy}
                      onClick={() => {
                        setNotice(null);
                        setConfirming(provider);
                      }}
                    >
                      {messages.unlinkProvider(name)}
                    </Group.ActionButton>
                  )}
                </Group.Rows>
              );
            })}
          </Group.Card>
        )}
        {/* T-E7-63: the answer of this section's actions, under its card; always there, so it is announced. */}
        <p
          className={notice?.problem === true ? 'group-notice is-problem' : 'group-notice'}
          ref={noticeRef}
          tabIndex={-1}
          role={notice?.problem === true ? 'alert' : 'status'}
        >
          {notice?.text}
        </p>
        {/* T-E7-63: a footnote only when there are accounts to link; for the password alone the card says it all.
            Only once the providers are known, so it does not show and then go. */}
        {current !== undefined && providersKnown && shown.length > 0 && (
          <Group.Footnote>{messages.methodsIntro}</Group.Footnote>
        )}
      </Group.Section>
      {current !== undefined && (
        // T-E7-62: last and folded; open when coming back from confirming with a linked account to finish deleting.
        <Collapsible
          title={messages.privacyTitle}
          className="account-privacy"
          heading="h2"
          defaultOpen={searchParams.get('social') === 'reauthenticated'}
        >
          <AccountDataSection
            methods={current}
            providers={PROVIDERS.filter((provider) => enabled.includes(provider) && linked(provider) !== undefined)}
            reauthenticated={searchParams.get('social') === 'reauthenticated'}
            onSignedOut={signedOut}
            onDeleted={() => {
              setDeleted(true);
              forgetAccount(queryClient);
            }}
          />
        </Collapsible>
      )}
    </div>
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
    <form className="account-form" onSubmit={send} noValidate>
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
