import type { ReactNode } from 'react';
import './AlertBox.css';

/**
 * A failure the shopper must notice (UC-14, E7): a light red box with a dark red border and a warning icon. The
 * alert region stays on the page, empty without a message, so screen readers announce the message when it enters.
 * `action` follows the message, such as a button to try again.
 */
export function AlertBox({ message, action }: { message: string | null; action?: ReactNode }) {
  return (
    <div className="alert-region" role="alert">
      {message !== null && (
        <p className="alert-box">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 3.5 2.5 20h19L12 3.5Z" />
            <path d="M12 10v4.5M12 17.2v.1" />
          </svg>
          <span>
            {message}
            {action !== undefined && <> {action}</>}
          </span>
        </p>
      )}
    </div>
  );
}
