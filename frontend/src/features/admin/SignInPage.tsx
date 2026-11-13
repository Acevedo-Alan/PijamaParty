import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SubmitEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatTime } from '../../i18n/formatDate.ts';
import { AuthCard, AuthField, PasswordInput, type AuthOutcome } from '../account/AuthCard.tsx';
import { leaveAnalyticsBehind } from '../analytics/analytics.ts';
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

/** Tells the page how a step's request goes, so the chibi of the card waits, or is sad about a refusal (T-E7-47). */
type ReportOutcome = (outcome: AuthOutcome) => void;

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

function outcomeOf(pending: boolean, failure: string | null): AuthOutcome {
  if (pending) {
    return 'waiting';
  }
  return failure === null ? undefined : 'error';
}

function Failure({ text }: { text: string | null }) {
  return text === null ? null : (
    <p className="account-error" role="alert">
      {text}
    </p>
  );
}

function PasswordStep({
  onAccepted,
  onError,
  onOutcome,
}: {
  onAccepted: (next: NextStep) => void;
  onError: () => void;
  onOutcome: ReportOutcome;
}) {
  const id = useId();
  const emailId = `${id}-email`;
  const passwordId = `${id}-password`;
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
  const failure = failureMessage(attempt.data, attempt.isError);
  const outcome = outcomeOf(attempt.isPending, failure);

  useEffect(() => {
    onOutcome(outcome);
  }, [outcome, onOutcome]);

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    attempt.mutate();
  }

  return (
    <form className="account-form" onSubmit={submit}>
      <AuthField id={emailId} label={messages.email} error={null} icon="email">
        <input
          id={emailId}
          type="email"
          inputMode="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          required
          value={email}
          onChange={(event) => {
            setEmail(event.target.value);
          }}
        />
      </AuthField>
      <AuthField id={passwordId} label={messages.password} error={null} icon="password">
        <PasswordInput
          id={passwordId}
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => {
            setPassword(event.target.value);
          }}
        />
      </AuthField>
      <button
        type="submit"
        className="btn"
        data-pending={attempt.isPending ? '' : undefined}
        disabled={attempt.isPending}
      >
        {attempt.isPending ? messages.submitting : messages.submit}
      </button>
      <Failure text={failure} />
    </form>
  );
}

interface CodeFieldProps<T extends Answer> {
  /** The label of the app's code. */
  label: string;
  hint?: string;
  /** Whether a recovery code can be typed instead of the app's (UC-04): the sign-in, not the enrollment. */
  allowRecovery?: boolean;
  submit: string;
  submitting: string;
  send: (code: string) => Promise<T>;
  onAnswer: (answer: T) => void;
  onOutcome: ReportOutcome;
}

/**
 * One code field and its errors: the 6 digits of the app, with the phone's number pad, or, after "Usá un código de
 * recuperación", a recovery code as text. Both go to the same request; only the field changes.
 */
function CodeForm<T extends Answer>({
  label,
  hint,
  allowRecovery = false,
  submit,
  submitting,
  send,
  onAnswer,
  onOutcome,
}: CodeFieldProps<T>) {
  const id = useId();
  const codeId = `${id}-code`;
  const hintId = `${id}-hint`;
  const input = useRef<HTMLInputElement>(null);
  const [recovery, setRecovery] = useState(false);
  const [code, setCode] = useState('');
  const attempt = useMutation({
    mutationFn: () => send(code),
    onSuccess: (answer) => {
      setCode('');
      onAnswer(answer);
    },
  });
  const failure = failureMessage(attempt.data, attempt.isError);
  const outcome = outcomeOf(attempt.isPending, failure);

  useEffect(() => {
    onOutcome(outcome);
  }, [outcome, onOutcome]);

  const shownHint = recovery ? messages.recoveryCodeHint : hint;

  function toggleRecovery() {
    setRecovery(!recovery);
    setCode('');
    attempt.reset();
    input.current?.focus();
  }

  return (
    <form
      className="account-form"
      onSubmit={(event) => {
        event.preventDefault();
        attempt.mutate();
      }}
    >
      <AuthField id={codeId} label={recovery ? messages.recoveryCodeLabel : label} error={null}>
        <input
          ref={input}
          id={codeId}
          className={recovery ? 'sign-in-code sign-in-code-recovery' : 'sign-in-code'}
          inputMode={recovery ? 'text' : 'numeric'}
          autoComplete="one-time-code"
          autoCapitalize={recovery ? 'characters' : 'none'}
          spellCheck={false}
          required
          // "123 456", as most apps show it, fits; the API ignores the space.
          maxLength={recovery ? 64 : 7}
          value={code}
          {...(shownHint === undefined ? {} : { 'aria-describedby': hintId })}
          onChange={(event) => {
            setCode(event.target.value);
          }}
        />
      </AuthField>
      {shownHint !== undefined && (
        <p id={hintId} className="account-hint">
          {shownHint}
        </p>
      )}
      <button
        type="submit"
        className="btn"
        data-pending={attempt.isPending ? '' : undefined}
        disabled={attempt.isPending}
      >
        {attempt.isPending ? submitting : submit}
      </button>
      <Failure text={failure} />
      {allowRecovery && (
        <button type="button" className="link-btn sign-in-alternative" onClick={toggleRecovery}>
          {recovery ? messages.useAppCode : messages.useRecoveryCode}
        </button>
      )}
    </form>
  );
}

function EnrollmentStep({
  onAnswer,
  onExpired,
  onOutcome,
}: {
  onAnswer: (answer: EnrollmentAnswer) => void;
  onExpired: () => void;
  onOutcome: ReportOutcome;
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
      <p className="account-intro">{messages.enrollIntro}</p>
      <img className="enrollment-qr" src={qrCode} alt={messages.qrAlt} width={240} height={240} />
      <p className="account-hint">{messages.secretLabel}</p>
      <p className="enrollment-secret">{secret.replace(/(.{4})/g, '$1 ').trim()}</p>
      <CodeForm
        label={messages.enrollCodeLabel}
        submit={messages.enroll}
        submitting={messages.enrolling}
        send={confirmEnrollment}
        onAnswer={onAnswer}
        onOutcome={onOutcome}
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
    <div className="sign-in-recovery">
      <p className="account-intro">{messages.recoveryIntro}</p>
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
    </div>
  );
}

const TITLES: Record<Step['name'], string> = {
  password: messages.title,
  secondFactor: messages.codeTitle,
  enrollment: messages.enrollTitle,
  recoveryCodes: messages.recoveryTitle,
};

const EYEBROWS: Record<Step['name'], string> = {
  password: messages.step(1, 2),
  secondFactor: messages.step(2, 2),
  enrollment: messages.step(2, 2),
  recoveryCodes: messages.lastStep,
};

/**
 * The back-office sign-in (UC-04) at /admin/ingresar: the password, then the code of the authenticator app or, the
 * first time, its enrollment and the recovery codes (T-E1-16). Checking the session first also leaves the CSRF cookie
 * the sign-in needs. It takes the store's sign-in card and chibi (T-E7-23, T-E7-47), with the step above the title;
 * from the code or the enrollment, "Usar otra cuenta" goes back to the password.
 */
export function SignInPage() {
  const session = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const state = (useLocation().state ?? {}) as SignInState;
  const target = returnPath(state.from);
  const [step, setStep] = useState<Step>({ name: 'password' });
  const [outcome, setOutcome] = useState<AuthOutcome>(undefined);

  // As every back-office page (AdminLayout): the analytics tools stay on the store.
  useEffect(() => {
    leaveAnalyticsBehind();
  }, []);

  function goTo(next: Step) {
    setOutcome(undefined);
    setStep(next);
  }

  function enter(administrator: Administrator) {
    const signedIn: SessionAnswer = { status: 'signedIn', administrator };
    queryClient.setQueryData(SESSION_KEY, signedIn);
    void navigate(target, { replace: true });
  }

  function restart() {
    goTo({ name: 'password', notice: messages.expiredStep });
  }

  function onCode(answer: CodeAnswer) {
    if (answer.status === 'signedIn') {
      enter(answer.administrator);
    } else if (answer.status === 'expired' || answer.status === 'locked') {
      goTo(answer.status === 'locked'
        ? { name: 'password', notice: messages.locked(formatTime(answer.until)) }
        : { name: 'password', notice: messages.expiredStep });
    }
  }

  function onEnrollment(answer: EnrollmentAnswer) {
    if (answer.status === 'enrolled') {
      goTo({ name: 'recoveryCodes', administrator: answer.administrator, codes: answer.recoveryCodes });
    } else if (answer.status === 'expired') {
      restart();
    } else if (answer.status === 'locked') {
      goTo({ name: 'password', notice: messages.locked(formatTime(answer.until)) });
    }
  }

  if (session.data?.status === 'signedIn' && step.name !== 'recoveryCodes') {
    return <Navigate to={target} replace />;
  }

  const canGoBack = step.name === 'secondFactor' || step.name === 'enrollment';
  return (
    <AuthCard
      title={TITLES[step.name]}
      eyebrow={EYEBROWS[step.name]}
      outcome={outcome}
      pose={step.name === 'password' ? 'signIn' : 'reset'}
    >
      <NoIndex />
      {step.name === 'password' && (
        <>
          {step.notice !== undefined ? (
            <p className="account-done" role="status">
              {step.notice}
            </p>
          ) : (
            state.expired === true && (
              <p className="account-done" role="status">
                {messages.expired}
              </p>
            )
          )}
          <PasswordStep
            onAccepted={(next) => {
              goTo(next === 'ENROLLMENT' ? { name: 'enrollment' } : { name: 'secondFactor' });
            }}
            onError={() => {
              // A missing CSRF cookie is answered 403: checking the session again sets it.
              void session.refetch();
            }}
            onOutcome={setOutcome}
          />
        </>
      )}
      {step.name === 'secondFactor' && (
        <CodeForm
          label={messages.codeLabel}
          hint={messages.codeHint}
          allowRecovery
          submit={messages.verify}
          submitting={messages.verifying}
          send={verifySecondFactor}
          onAnswer={onCode}
          onOutcome={setOutcome}
        />
      )}
      {step.name === 'enrollment' && (
        <EnrollmentStep onAnswer={onEnrollment} onExpired={restart} onOutcome={setOutcome} />
      )}
      {step.name === 'recoveryCodes' && (
        <RecoveryCodesStep
          codes={step.codes}
          onContinue={() => {
            enter(step.administrator);
          }}
        />
      )}
      {canGoBack && (
        <button
          type="button"
          className="btn btn-secondary sign-in-back"
          onClick={() => {
            goTo({ name: 'password' });
          }}
        >
          {messages.back}
        </button>
      )}
    </AuthCard>
  );
}
