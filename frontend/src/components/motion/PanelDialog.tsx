import { AnimatePresence, m, useIsPresent } from 'motion/react';
import { useLayoutEffect, useRef, type MouseEvent, type ReactNode, type RefObject, type SyntheticEvent } from 'react';
import { duration, ease } from '../../styles/motion.ts';
import { useActionMotion } from './useActionMotion.ts';
import './PanelDialog.css';

interface PanelDialogProps {
  open: boolean;
  /** The edge the panel slides from. */
  from: 'left' | 'right' | 'bottom';
  className: string;
  labelledBy: string;
  /** The dialog element, for a caller that needs it while the panel is open. */
  ref?: RefObject<HTMLDialogElement | null>;
  /** Escape or a tap on the dimmed page asks to close: the caller sets `open` to false. */
  onDismiss: () => void;
  /** The dialog closed, after its exit animation or by the browser: the page behind can take the focus again. */
  onClosed?: () => void;
  children: ReactNode;
}

/**
 * A side panel as a modal dialog (E7 motion): it slides from its edge over a fading overlay and back on close. The
 * dialog closes only when the exit animation ends, so the focus returns after it, as the browser restores it.
 */
export function PanelDialog({ open, ref, ...panel }: PanelDialogProps) {
  const own = useRef<HTMLDialogElement>(null);
  const dialog = ref ?? own;
  return (
    <AnimatePresence
      onExitComplete={() => {
        dialog.current?.close();
      }}
    >
      {open && <OpenPanel key="panel" dialog={dialog} {...panel} />}
    </AnimatePresence>
  );
}

interface OpenPanelProps extends Omit<PanelDialogProps, 'open' | 'ref'> {
  dialog: RefObject<HTMLDialogElement | null>;
}

function OpenPanel({ dialog, from, className, labelledBy, onDismiss, onClosed, children }: OpenPanelProps) {
  const isPresent = useIsPresent();
  // The whole `transform`, not `x` or `y`: Motion hands it to the compositor, so the slide stays smooth while the
  // panel's content mounts on the main thread. What rises from the bottom travels further: it opens slower and settles
  // softly, and leaves as fast as the side panels.
  const close = { duration: duration.panel, ease: ease.standard };
  const motion = useActionMotion({
    hidden: {
      transform: from === 'bottom' ? 'translateY(100%)' : `translateX(${from === 'right' ? '' : '-'}100%)`,
      transition: close,
    },
    shown: { transform: 'none' },
    transition: from === 'bottom' ? { duration: duration.slow, ease: ease.out } : close,
  });

  // Before the first paint, so the panel is in the top layer when its slide starts.
  useLayoutEffect(() => {
    const element = dialog.current;
    if (element !== null && !element.open) {
      element.showModal();
    }
  }, [dialog]);

  function dismiss(event: SyntheticEvent<HTMLDialogElement>) {
    // Escape: the panel slides out first; the dialog closes when the animation ends.
    event.preventDefault();
    onDismiss();
  }

  function dismissOnBackdrop(event: MouseEvent<HTMLDialogElement>) {
    if (event.target === event.currentTarget) {
      onDismiss();
    }
  }

  return (
    <m.dialog
      ref={dialog}
      className={`panel-dialog ${className}${isPresent ? '' : ' closing'}`}
      aria-labelledby={labelledBy}
      onCancel={dismiss}
      onClose={onClosed}
      onClick={dismissOnBackdrop}
      {...motion}
    >
      {children}
    </m.dialog>
  );
}
