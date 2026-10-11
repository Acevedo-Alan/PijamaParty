import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type SubmitEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatTime } from '../../i18n/formatDate.ts';
import { NoIndex } from './AdminLayout.tsx';
import { signIn, type SessionAnswer, type SignInAnswer } from './authApi.ts';
import { returnPath, SESSION_KEY, useSession, type SignInState } from './useSession.ts';
import './admin.css';

function failureMessage(answer: SignInAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return es.admin.signIn.failed;
  }
  switch (answer?.status) {
    case 'invalidCredentials':
      return es.admin.signIn.invalidCredentials;
    case 'locked':
      return es.admin.signIn.locked(formatTime(answer.until));
    case 'tooManyAttempts':
      return es.admin.signIn.tooManyAttempts;
    default:
      return null;
  }
}

/**
 * The back-office sign-in (UC-04) at /admin/ingresar. Wrong credentials get one generic message. Checking the
 * session first also leaves the CSRF cookie the sign-in needs.
 */
export function SignInPage() {
  const session = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const state = (useLocation().state ?? {}) as SignInState;
  const target = returnPath(state.from);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const attempt = useMutation({
    mutationFn: () => signIn(email, password),
    onSuccess: (answer) => {
      if (answer.status === 'signedIn') {
        const signedIn: SessionAnswer = { status: 'signedIn', administrator: answer.administrator };
        queryClient.setQueryData(SESSION_KEY, signedIn);
        void navigate(target, { replace: true });
      } else {
        setPassword('');
      }
    },
    onError: () => {
      // A missing CSRF cookie is answered 403: checking the session again sets it.
      void session.refetch();
    },
  });

  if (session.data?.status === 'signedIn' && !attempt.isPending) {
    return <Navigate to={target} replace />;
  }

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    attempt.mutate();
  }

  const failure = failureMessage(attempt.data, attempt.isError);

  return (
    <main className="wrap admin-main admin-sign-in">
      <NoIndex />
      <title>{`${es.admin.signIn.title} | ${es.store.name}`}</title>
      <h1>{es.admin.signIn.title}</h1>
      {state.expired === true && failure === null && <p role="status">{es.admin.signIn.expired}</p>}
      <form className="admin-form" onSubmit={submit}>
        <label className="field">
          <span>{es.admin.signIn.email}</span>
          <input
            type="email"
            autoComplete="username"
            required
            value={email}
            onChange={(event) => {
              setEmail(event.target.value);
            }}
          />
        </label>
        <label className="field">
          <span>{es.admin.signIn.password}</span>
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
            }}
          />
        </label>
        {failure !== null && (
          <p className="form-error" role="alert">
            {failure}
          </p>
        )}
        <button type="submit" className="btn" disabled={attempt.isPending}>
          {attempt.isPending ? es.admin.signIn.submitting : es.admin.signIn.submit}
        </button>
      </form>
    </main>
  );
}
