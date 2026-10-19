import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Link, useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { SIGN_IN_PATH } from './AccountLink.tsx';
import { confirmLink } from './accountApi.ts';
import { SocialSignIn } from './SocialSignIn.tsx';
import './account.css';

const messages = es.account.link;

/**
 * UC-27 (T-E4-14): the link of the link confirmation email. Opening the page uses nothing, so neither a mail scanner
 * that runs scripts nor a curious click links an account: it says what will be connected, warns whoever did not ask
 * for it, and only the button uses the token, once. Only Facebook sends these links (Google vouches for its emails),
 * so the page names Facebook. Linking does not sign in: the page offers to continue with Facebook, as the email says.
 */
export function ConfirmLinkPage() {
  const { hash } = useLocation();
  const token = hash.replace(/^#/, '');
  const confirmation = useMutation({ mutationFn: confirmLink });
  const outcome = useRef<HTMLDivElement>(null);

  const status = confirmation.data?.status;
  let problem: string | null = null;
  if (confirmation.isError) {
    problem = messages.error;
  } else if (status === 'alreadyLinked') {
    problem = messages.alreadyLinked;
  } else if (token === '' || status === 'invalidLink') {
    problem = messages.invalidLink;
  }
  const answered = status !== undefined || confirmation.isError;

  useEffect(() => {
    if (answered) {
      outcome.current?.focus();
    }
  }, [answered]);

  return (
    <main className="wrap account-page">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      {status === 'linked' ? (
        <div className="account-done" role="status" tabIndex={-1} ref={outcome}>
          <p>{messages.linked}</p>
          <SocialSignIn only={['FACEBOOK']} beforeForm={false} />
        </div>
      ) : problem !== null ? (
        <div className="account-done" role="alert" tabIndex={-1} ref={outcome}>
          <p>{problem}</p>
          <Link to={SIGN_IN_PATH} className="btn">
            {messages.signIn}
          </Link>
        </div>
      ) : (
        <div className="account-done">
          <p>{messages.intro}</p>
          <p>{messages.notYou}</p>
          <button
            type="button"
            className="btn"
            disabled={confirmation.isPending}
            onClick={() => {
              confirmation.mutate(token);
            }}
          >
            {confirmation.isPending ? messages.linking : messages.confirm}
          </button>
        </div>
      )}
    </main>
  );
}
