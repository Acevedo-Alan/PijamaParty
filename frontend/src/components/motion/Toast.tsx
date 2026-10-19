import { AnimatePresence, m } from 'motion/react';
import type { ReactNode } from 'react';
import { duration, ease } from '../../styles/motion.ts';
import { useActionMotion } from './useActionMotion.ts';
import './Toast.css';

interface ToastProps {
  open: boolean;
  closeLabel: string;
  onClose: () => void;
  children: ReactNode;
}

/**
 * A short notice at the bottom of the screen (E7 motion): it rises in answer to an action and sinks on close. The
 * status region stays on the page, so screen readers announce what enters it.
 */
export function Toast({ open, closeLabel, onClose, children }: ToastProps) {
  const motion = useActionMotion({
    hidden: { y: 24, opacity: 0 },
    shown: { y: 0, opacity: 1 },
    transition: { duration: duration.base, ease: ease.pop },
  });
  return (
    <div className="toast-region" role="status">
      <AnimatePresence>
        {open && (
          <m.div key="toast" className="toast" {...motion}>
            <p>{children}</p>
            <button type="button" className="icon-btn" aria-label={closeLabel} onClick={onClose}>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </m.div>
        )}
      </AnimatePresence>
    </div>
  );
}
