import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useState, type SubmitEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatTime } from '../../i18n/formatDate.ts';
import { NoIndex } from '../seo/NoIndex.tsx';
import {
  confirmEnrollment,
  signIn,
  startEnrollment,
  verifySecondFactor,
  type Administrator,
  type CodeAnswer,
  type EnrollmentAnswer,
  type NextStep,
  type SessionAnswer,
  type SignInAnswer,
} from './authApi.ts';
import { returnPath, SESSION_KEY, useSession, type SignInState } from './useSession.ts';
import './admin.css';

const messages = esAdmin.signIn;

type Step =
  | { name: 'password'; notice?: string }
  | { name: 'secondFactor' }
  | { name: 'enrollment' }
  | { name: 'recoveryCodes'; administrator: Administrator; codes: string[] };

type Answer = SignInAnswer | CodeAnswer | EnrollmentAnswer;

function failureMessage(answer: Answer | undefined, failed: boolean): string | null {
  if (failed) {
    return messages.failed;
  }
  switch (answer?.status) {
    case 'invalidCredentials':
      return messages.invalidCredentials;
    case 'invalidCode':
      return messages.invalidCode;
    case 'locked':
      return messages.locked(formatTime(answer.until));
    case 'tooManyAttempts':
      return messages.tooManyAttempts;
    default:
      return null;
  }
}

function Failure({ text }: { text: string | null }) {
  return text === null ? null : (
    <p className="form-error" role="alert">
      {text}
    </p>
  );
}

function PasswordStep({ onAccepted, onError }: { onAccepted: (next: NextStep) => void; onError: () => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const attempt = useMutation({
    mutationFn: () => signIn(email, password),
    onSuccess: (answer) => {
      setPassword('');
      if (answer.status === 'passwordAccepted') {
        onAccepted(answer.nextStep);
      }
    },
    onError,
  });

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    attempt.mutate();
  }

  return (
    <form className="admin-form" onSubmit={submit}>
      <label className="field">
        <span>{messages.email}</span>
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
        <span>{messages.password}</span>
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
      <Failure text={failureMessage(attempt.data, attempt.isError)} />
      <button type="submit" className="btn" disabled={attempt.isPending}>
        {attempt.isPending ? messages.submitting : messages.submit}
      </button>
    </form>
  );
}

interface CodeFieldProps<T extends Answer> {
  label: string;
  hint?: string;
  submit: string;
  submitting: string;
  send: (code: string) => Promise<T>;
  onAnswer: (answer: T) => void;
}

/** One code field (6 digits of the app, or a recovery code) and its errors. */
function CodeForm<T extends Answer>({ label, hint, submit, submitting, send, onAnswer }: CodeFieldProps<T>) {
  const hintId = useId();
  const [code, setCode] = useState('');
  const attempt = useMutation({
    mutationFn: () => send(code),
    onSuccess: (answer) => {
      setCode('');
      onAnswer(answer);
    },
  });

  return (
    <form
      className="admin-form"
      onSubmit={(event) => {
        event.preventDefault();
        attempt.mutate();
      }}
    >
      <label className="field">
        <span>{label}</span>
        <input
          inputMode="text"
          autoComplete="one-time-code"
          autoCapitalize="characters"
          spellCheck={false}
          required
          maxLength={64}
          value={code}
          {...(hint === undefined ? {} : { 'aria-describedby': hintId })}
          onChange={(event) => {
            setCode(event.target.value);
          }}
        />
      </label>
      {hint !== undefined && (
        <p id={hintId} className="field-hint">
          {hint}
        </p>
      )}
      <Failure text={failureMessage(attempt.data, attempt.isError)} />
      <button type="submit" className="btn" disabled={attempt.isPending}>
        {attempt.isPending ? submitting : submit}
      </button>
    </form>
  );
}

function EnrollmentStep({
  onAnswer,
  onExpired,
}: {
  onAnswer: (answer: EnrollmentAnswer) => void;
  onExpired: () => void;
}) {
  // Each request makes a new secret: ask once for this step, never again behind the visitor's back.
  const enrollment = useQuery({
    queryKey: ['admin', 'enrollment'],
    queryFn: ({ signal }) => startEnrollment(signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
  const expired = enrollment.data === null;

  useEffect(() => {
    if (expired) {
      onExpired();
    }
  }, [expired, onExpired]);

  if (enrollment.isPending) {
    return <p role="status">{messages.loadingEnrollment}</p>;
  }
  if (enrollment.isError) {
    return <Failure text={messages.failed} />;
  }
  if (enrollment.data === null) {
    return null;
  }
  const { secret, qrCode } = enrollment.data;
  return (
    <>
      <p>{messages.enrollIntro}</p>
      <img className="enrollment-qr" src={qrCode} alt={messages.qrAlt} width={240} height={240} />
      <p className="field-hint">{messages.secretLabel}</p>
      <p className="enrollment-secret">{secret.replace(/(.{4})/g, '$1 ').trim()}</p>
      <CodeForm
        label={messages.enrollCodeLabel}
        submit={messages.enroll}
        submitting={messages.enrolling}
        send={confirmEnrollment}
        onAnswer={onAnswer}
      />
    </>
  );
}

function RecoveryCodesStep({ codes, onContinue }: { codes: string[]; onContinue: () => void }) {
  const [saved, setSaved] = useState(false);

  function download() {
    const file = new Blob([`${messages.recoveryTitle}\n\n${codes.join('\n')}\n`], { type: 'text/plain' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(file);
    link.download = messages.recoveryFileName;
    link.click();
    URL.revokeObjectURL(link.href);
  }

  return (
    <>
      <p>{messages.recoveryIntro}</p>
      <ul className="recovery-codes" aria-label={messages.recoveryTitle}>
        {codes.map((code) => (
          <li key={code}>{code}</li>
        ))}
      </ul>
      <button type="button" className="btn btn-secondary" onClick={download}>
        {messages.download}
      </button>
      <label className="switch">
        <input
          type="checkbox"
          checked={saved}
          onChange={(event) => {
            setSaved(event.target.checked);
          }}
        />
        <span>{messages.saved}</span>
      </label>
      <button type="button" className="btn" disabled={!saved} onClick={onContinue}>
        {messages.continue}
      </button>
    </>
  );
}

const TITLES: Record<Step['name'], string> = {
  password: messages.title,
  secondFactor: messages.codeTitle,
  enrollment: messages.enrollTitle,
  recoveryCodes: messages.recoveryTitle,
};

/**
 * The back-office sign-in (UC-04) at /admin/ingresar: the password, then the code of the authenticator app or, the
 * first time, its enrollment and the recovery codes (T-E1-16). Checking the session first also leaves the CSRF cookie
 * the sign-in needs.
 */
export function SignInPage() {
  const session = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const state = (useLocation().state ?? {}) as SignInState;
  const target = returnPath(state.from);
  const [step, setStep] = useState<Step>({ name: 'password' });

  function enter(administrator: Administrator) {
    const signedIn: SessionAnswer = { status: 'signedIn', administrator };
    queryClient.setQueryData(SESSION_KEY, signedIn);
    void navigate(target, { replace: true });
  }

  function restart() {
    setStep({ name: 'password', notice: messages.expiredStep });
  }

  function onCode(answer: CodeAnswer) {
    if (answer.status === 'signedIn') {
      enter(answer.administrator);
    } else if (answer.status === 'expired' || answer.status === 'locked') {
      setStep(answer.status === 'locked'
        ? { name: 'password', notice: messages.locked(formatTime(answer.until)) }
        : { name: 'password', notice: messages.expiredStep });
    }
  }

  function onEnrollment(answer: EnrollmentAnswer) {
    if (answer.status === 'enrolled') {
      setStep({ name: 'recoveryCodes', administrator: answer.administrator, codes: answer.recoveryCodes });
    } else if (answer.status === 'expired') {
      restart();
    } else if (answer.status === 'locked') {
      setStep({ name: 'password', notice: messages.locked(formatTime(answer.until)) });
    }
  }

  if (session.data?.status === 'signedIn' && step.name !== 'recoveryCodes') {
    return <Navigate to={target} replace />;
  }

  return (
    <main className="wrap admin-main admin-sign-in">
      <NoIndex />
      <title>{`${TITLES[step.name]} | ${es.store.name}`}</title>
      <h1>{TITLES[step.name]}</h1>
      {step.name === 'password' && (
        <>
          {step.notice !== undefined ? (
            <p role="status">{step.notice}</p>
          ) : (
            state.expired === true && <p role="status">{messages.expired}</p>
          )}
          <PasswordStep
            onAccepted={(next) => {
              setStep(next === 'ENROLLMENT' ? { name: 'enrollment' } : { name: 'secondFactor' });
            }}
            onError={() => {
              // A missing CSRF cookie is answered 403: checking the session again sets it.
              void session.refetch();
            }}
          />
        </>
      )}
      {step.name === 'secondFactor' && (
        <CodeForm
          label={messages.codeLabel}
          hint={messages.codeHint}
          submit={messages.verify}
          submitting={messages.verifying}
          send={verifySecondFactor}
          onAnswer={onCode}
        />
      )}
      {step.name === 'enrollment' && <EnrollmentStep onAnswer={onEnrollment} onExpired={restart} />}
      {step.name === 'recoveryCodes' && (
        <RecoveryCodesStep
          codes={step.codes}
          onContinue={() => {
            enter(step.administrator);
          }}
        />
      )}
    </main>
  );
}
