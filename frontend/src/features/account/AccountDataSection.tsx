import { useMutation } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react';
import { useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { signInWithProviderUrl, type SignInProvider } from './accountApi.ts';
import {
  DATA_FILE_NAME,
  deleteAccount,
  downloadCustomerData,
  requestReauthentication,
  type DeleteAccountAnswer,
} from './customerDataApi.ts';
import { saveFile } from './fileDownload.ts';
import { goToProvider } from './providerRedirect.ts';
import type { SignInMethods } from './signInMethodsApi.ts';
import { PROVIDER_NAMES } from './SocialSignIn.tsx';

const messages = es.account.myAccount;

/** What the deletion leaves in the navigation state for the page it goes to (UC-28). */
export interface AccountDeletedState {
  accountDeleted?: boolean;
}

/** The message of the page the deletion goes to: the home page. */
export function AccountDeletedNotice() {
  const state = useLocation().state as AccountDeletedState | null;
  if (state?.accountDeleted !== true) {
    return null;
  }
  return (
    <p className="wrap account-deleted" role="status">
      {messages.accountDeleted}
    </p>
  );
}

interface AccountDataSectionProps {
  methods: SignInMethods;
  /** The providers the customer can confirm with: linked and enabled. */
  providers: readonly SignInProvider[];
  /** The customer just came back from a linked provider (`social=reauthenticated`): it deletes without a password. */
  reauthenticated: boolean;
  onSignedOut: () => void;
  /** The account no longer exists and the session is gone: the page goes home and forgets it. */
  onDeleted: () => void;
}

/**
 * UC-28 (T-E4-16): "Tus datos" in "Mi cuenta": "Descargar mis datos" saves the file; "Eliminar mi cuenta" explains
 * what is deleted and what is kept, and confirms with the password or by signing in again with a linked provider.
 * After the deletion the store goes home and says the account was deleted.
 */
export function AccountDataSection({
  methods,
  providers,
  reauthenticated,
  onSignedOut,
  onDeleted,
}: AccountDataSectionProps) {
  const [deleting, setDeleting] = useState(reauthenticated);
  const download = useMutation({
    mutationFn: downloadCustomerData,
    onSuccess: (answer) => {
      if (answer.status === 'downloaded') {
        saveFile(answer.file, DATA_FILE_NAME);
      } else {
        onSignedOut();
      }
    },
  });

  return (
    <section aria-labelledby="account-data-title">
      <h2 id="account-data-title" className="my-account-heading">
        {messages.dataTitle}
      </h2>
      <p className="account-intro">{messages.dataIntro}</p>
      <div className="address-actions">
        <button
          type="button"
          className="btn btn-secondary"
          disabled={download.isPending}
          onClick={() => {
            download.mutate();
          }}
        >
          {download.isPending ? messages.downloading : messages.download}
        </button>
        {!deleting && (
          <button
            type="button"
            className="btn btn-danger"
            onClick={() => {
              setDeleting(true);
            }}
          >
            {messages.deleteAccount}
          </button>
        )}
      </div>
      {download.isError && (
        <p className="account-error" role="alert">
          {messages.downloadError}
        </p>
      )}
      {deleting && (
        <DeleteAccountForm
          methods={methods}
          providers={providers}
          reauthenticated={reauthenticated}
          onCancel={() => {
            setDeleting(false);
          }}
          onSignedOut={onSignedOut}
          onDeleted={onDeleted}
        />
      )}
    </section>
  );
}

function deleteProblem(answer: DeleteAccountAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return messages.deleteError;
  }
  switch (answer?.status) {
    case 'wrongPassword':
      return messages.wrongPassword;
    case 'reauthenticationRequired':
      return messages.reauthenticationRequired;
    default:
      return null;
  }
}

interface DeleteAccountFormProps {
  methods: SignInMethods;
  providers: readonly SignInProvider[];
  reauthenticated: boolean;
  onCancel: () => void;
  onSignedOut: () => void;
  onDeleted: () => void;
}

function DeleteAccountForm({
  methods,
  providers,
  reauthenticated,
  onCancel,
  onSignedOut,
  onDeleted,
}: DeleteAccountFormProps) {
  const [password, setPassword] = useState('');
  const [reauthenticationProblem, setReauthenticationProblem] = useState<string | null>(null);
  // Back from the provider; forgotten when the server says the 5 minutes are over, so it can confirm again.
  const [confirmed, setConfirmed] = useState(reauthenticated);
  const heading = useRef<HTMLHeadingElement>(null);
  const id = useId();
  // Confirmed with the provider, it deletes without a password; otherwise with the password, when it has one.
  const withPassword = !confirmed && methods.hasPassword;

  useEffect(() => {
    heading.current?.focus();
  }, []);

  const remove = useMutation({
    mutationFn: () => deleteAccount(withPassword ? password : null),
    onSuccess: (answer) => {
      switch (answer.status) {
        case 'deleted':
          onDeleted();
          break;
        case 'signedOut':
          onSignedOut();
          break;
        case 'reauthenticationRequired':
          setConfirmed(false);
          break;
        case 'wrongPassword':
          break;
      }
    },
  });

  const reauthenticate = useMutation({
    mutationFn: requestReauthentication,
    onSuccess: (answer, provider) => {
      switch (answer.status) {
        case 'requested':
          // A whole-page navigation to the provider, which comes back to "Mi cuenta".
          goToProvider(signInWithProviderUrl(provider));
          break;
        case 'notLinked':
          setReauthenticationProblem(messages.reauthenticationNotLinked);
          break;
        case 'notEnabled':
          setReauthenticationProblem(messages.linkNotEnabled);
          break;
        case 'signedOut':
          onSignedOut();
          break;
      }
    },
    onError: () => {
      setReauthenticationProblem(messages.reauthenticationError);
    },
  });

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    remove.mutate();
  }

  const busy = remove.isPending || reauthenticate.isPending;
  return (
    <div className="address-confirm my-account-delete" role="group" aria-labelledby={`${id}-title`}>
      <h3 id={`${id}-title`} ref={heading} tabIndex={-1}>
        {messages.deleteTitle}
      </h3>
      <p>{messages.deleteExplanation}</p>
      <p>{messages.deleteOrders}</p>
      {confirmed || withPassword ? (
        <form className="account-form" onSubmit={send} noValidate>
          {withPassword && (
            <>
              <label htmlFor={`${id}-password`}>{messages.deletePassword}</label>
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
            </>
          )}
          <div className="address-actions">
            <button type="submit" className="btn btn-danger" disabled={busy || (withPassword && password === '')}>
              {remove.isPending ? messages.deleting : withPassword ? messages.deleteWithPassword : messages.deleteNow}
            </button>
            <button type="button" className="btn btn-secondary" onClick={onCancel}>
              {messages.cancel}
            </button>
          </div>
        </form>
      ) : (
        <div className="address-actions">
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
            {messages.cancel}
          </button>
        </div>
      )}
      <p className="account-error" role="alert">
        {deleteProblem(remove.data, remove.isError)}
      </p>
      {!confirmed && providers.length > 0 && (
        <>
          <p>{withPassword ? messages.orConfirmWith : messages.confirmWithIntro}</p>
          <div className="address-actions">
            {providers.map((provider) => (
              <button
                key={provider}
                type="button"
                className="btn btn-secondary"
                disabled={busy}
                onClick={() => {
                  setReauthenticationProblem(null);
                  reauthenticate.mutate(provider);
                }}
              >
                {messages.confirmWith(PROVIDER_NAMES[provider])}
              </button>
            ))}
          </div>
          <p className="account-error" role="alert">
            {reauthenticationProblem}
          </p>
        </>
      )}
    </div>
  );
}
