import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ComponentPropsWithoutRef,
  type FocusEvent,
  type ReactNode,
} from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { AuthChibi, type ChibiMood, type ChibiPose } from './AuthChibi.tsx';
import './account.css';

/** How the page's request went, as the chibi shows it (T-E7-47); nothing while no request was answered. */
export type AuthOutcome = 'waiting' | 'error' | 'success' | undefined;

/** Where the focus is inside the page: a text field, a password field (or its eye button), or elsewhere. */
type FocusKind = 'none' | 'text' | 'password';

/** The password input tells the page whether it is shown, so the chibi peeks at it (T-E7-47). */
const PasswordShownContext = createContext<((shown: boolean) => void) | null>(null);

function focusKindOf(target: EventTarget): FocusKind {
  if (!(target instanceof HTMLElement)) {
    return 'none';
  }
  if (target.closest('.password-field') !== null) {
    return 'password';
  }
  return target instanceof HTMLInputElement ? 'text' : 'none';
}

/** What the chibi shows: the request first, then where the focus is (T-E7-47). */
function moodOf(outcome: AuthOutcome, errorSeen: boolean, focus: FocusKind, passwordShown: boolean): ChibiMood {
  if (outcome === 'waiting' || outcome === 'success') {
    return outcome;
  }
  if (outcome === 'error' && !errorSeen) {
    return 'error';
  }
  if (focus === 'password') {
    return passwordShown ? 'peek' : 'password';
  }
  return focus === 'text' ? 'text' : 'idle';
}

interface AuthCardProps {
  title: string;
  intro?: string | undefined;
  /** The page's request, which the chibi waits for, is sad about or celebrates (T-E7-47). */
  outcome?: AuthOutcome;
  /** Which page it is, for the chibi's touch of its own (T-E7-47). */
  pose: ChibiPose;
  children: ReactNode;
}

/**
 * The sign-in, registration, password recovery and reset pages (UC-19, T-E7-23): a card with the title and the intro,
 * the page's content, and below it the way to the other page. Beside it, the chibi in its sky (T-E7-47): on the left
 * from 860 px, a band above the card below that. The chibi follows the focus and the request; a refusal makes it sad
 * until a field is typed in again.
 */
export function AuthCard({ title, intro, outcome, pose, children }: AuthCardProps) {
  const [focus, setFocus] = useState<FocusKind>('none');
  const [passwordShown, setPasswordShown] = useState(false);
  const [errorSeen, setErrorSeen] = useState(false);
  // A new request brings the next refusal back to the chibi's face.
  if (outcome === 'waiting' && errorSeen) {
    setErrorSeen(false);
  }

  return (
    <main
      className="account-page auth-page"
      onFocus={(event: FocusEvent) => {
        setFocus(focusKindOf(event.target));
      }}
      onBlur={(event: FocusEvent<HTMLElement>) => {
        if (!(event.relatedTarget instanceof Node && event.currentTarget.contains(event.relatedTarget))) {
          setFocus('none');
        }
      }}
      onInput={() => {
        if (outcome === 'error') {
          setErrorSeen(true);
        }
      }}
    >
      <title>{`${title} | ${es.store.name}`}</title>
      <AuthChibi mood={moodOf(outcome, errorSeen, focus, passwordShown)} pose={pose} />
      <div className="wrap auth-panel">
        <div className="auth-card">
          <h1>{title}</h1>
          {intro !== undefined && <p className="account-intro">{intro}</p>}
          <PasswordShownContext value={setPasswordShown}>{children}</PasswordShownContext>
        </div>
      </div>
    </main>
  );
}

/** The end of the card: a question and a full-width outlined button to the other page, e.g. "Crear cuenta". */
export function AuthSwitch({ question, to, children }: { question: string; to: string; children: ReactNode }) {
  return (
    <div className="auth-switch">
      <p>{question}</p>
      <Link to={to} className="btn btn-secondary">
        {children}
      </Link>
    </div>
  );
}

interface AuthFieldProps {
  /** The control's id; its message, when there is one, is `${id}-error`. */
  id: string;
  label: string;
  /** What sits on the label's row, on the right, e.g. "¿Olvidaste tu contraseña?". */
  aside?: ReactNode;
  /** What the field says below it once the form was sent without it. */
  error: string | null;
  /** The thin line icon at the start of the control: an envelope, a padlock or a person. */
  icon?: FieldIcon;
  children: ReactNode;
}

type FieldIcon = 'email' | 'password' | 'name';

const ICON_PATHS: Record<FieldIcon, string> = {
  email: 'M4 6.5h16v11H4zM4.5 7l7.5 6 7.5-6',
  password: 'M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3M12 14.5v2',
  name: 'M12 12a3.75 3.75 0 1 0 0-7.5 3.75 3.75 0 0 0 0 7.5zM5 20c0-3.6 3.1-5.5 7-5.5s7 1.9 7 5.5',
};

/**
 * A label, its control and its message below it, which a screen reader reads with the control. With an icon, the
 * control starts with it (T-E7-47): decorative, since the label already says what the field is.
 */
export function AuthField({ id, label, aside, error, icon, children }: AuthFieldProps) {
  return (
    <div className={icon === undefined ? 'auth-field' : 'auth-field auth-field-icon'}>
      <div className="auth-label-row">
        <label htmlFor={id}>{label}</label>
        {aside}
      </div>
      <div className="auth-control">
        {icon !== undefined && (
          <svg className="auth-control-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d={ICON_PATHS[icon]} />
          </svg>
        )}
        {children}
      </div>
      {error !== null && (
        <p id={`${id}-error`} className="auth-field-error">
          {error}
        </p>
      )}
    </div>
  );
}

/** The props a control of an {@link AuthField} takes for its message: the description and the invalid mark. */
export function fieldErrorProps(id: string, error: string | null, hint?: string) {
  const describedBy = [hint, error === null ? undefined : `${id}-error`].filter((part) => part !== undefined).join(' ');
  return {
    'aria-invalid': error !== null || undefined,
    'aria-describedby': describedBy === '' ? undefined : describedBy,
  };
}

/** A password input with the eye button that shows it or hides it again (T-E7-23). */
export function PasswordInput(props: Omit<ComponentPropsWithoutRef<'input'>, 'type'>) {
  const [shown, setShown] = useState(false);
  const reportShown = useContext(PasswordShownContext);
  // Gone with its page part (a form that gives way to its confirmation), it is no longer shown.
  useEffect(
    () => () => {
      reportShown?.(false);
    },
    [reportShown],
  );
  return (
    <div className="password-field">
      <input {...props} type={shown ? 'text' : 'password'} />
      <button
        type="button"
        className="password-toggle"
        aria-label={es.account.showPassword}
        aria-pressed={shown}
        aria-controls={props.id}
        onClick={() => {
          setShown(!shown);
          reportShown?.(!shown);
        }}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" strokeLinejoin="round" />
          <circle cx="12" cy="12" r="3" />
          {shown && <path d="M4 4l16 16" strokeLinecap="round" />}
        </svg>
      </button>
    </div>
  );
}

/**
 * The empty fields of a form once it was sent (T-E7-23): {@code check} marks the empty ones and moves the focus to the
 * first, and says whether the form can go; typing in a field clears its mark.
 */
export function useMissingFields<F extends string>(id: string) {
  const [missing, setMissing] = useState<readonly F[]>([]);
  return {
    isMissing: (field: F) => missing.includes(field),
    clear: (field: F) => {
      setMissing((current) => (current.includes(field) ? current.filter((each) => each !== field) : current));
    },
    check: (empty: readonly F[]) => {
      setMissing(empty);
      const [first] = empty;
      if (first === undefined) {
        return true;
      }
      document.getElementById(`${id}-${first}`)?.focus();
      return false;
    },
  };
}
