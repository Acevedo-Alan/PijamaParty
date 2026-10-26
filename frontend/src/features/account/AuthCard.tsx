import { useState, type ComponentPropsWithoutRef, type ReactNode } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import './account.css';

/**
 * The sign-in, registration, password recovery and reset pages (UC-19, T-E7-23): a centered card with the title and
 * the intro, the page's content, and below it the way to the other page.
 */
export function AuthCard({ title, intro, children }: { title: string; intro?: string | undefined; children: ReactNode }) {
  return (
    <main className="wrap account-page auth-page">
      <title>{`${title} | ${es.store.name}`}</title>
      <div className="auth-card">
        <h1>{title}</h1>
        {intro !== undefined && <p className="account-intro">{intro}</p>}
        {children}
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
  children: ReactNode;
}

/** A label, its control and its message below it, which a screen reader reads with the control. */
export function AuthField({ id, label, aside, error, children }: AuthFieldProps) {
  return (
    <div className="auth-field">
      <div className="auth-label-row">
        <label htmlFor={id}>{label}</label>
        {aside}
      </div>
      {children}
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
