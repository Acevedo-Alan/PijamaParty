import { useId, useState, type ReactNode } from 'react';
import './Collapsible.css';

interface CollapsibleProps {
  title: string;
  className?: string;
  /** Open when it first shows (T-E7-62: the privacy section, coming back to finish deleting the account). */
  defaultOpen?: boolean;
  /** The toggle inside a heading of this level, so the section is in the page's outline even while closed. */
  heading?: 'h2' | 'h3';
  children: ReactNode;
}

/**
 * A collapsed section that slides open and closed (E7 motion, `grid-template-rows: 0fr → 1fr`). While closed its
 * content is inert: out of the tab order and hidden from screen readers.
 */
export function Collapsible({ title, className, defaultOpen = false, heading, children }: CollapsibleProps) {
  const [open, setOpen] = useState(defaultOpen);
  const panelId = useId();
  const toggle = (
    <button
      type="button"
      className="collapsible-toggle"
      aria-expanded={open}
      aria-controls={panelId}
      onClick={() => {
        setOpen(!open);
      }}
    >
      {title}
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6 9l6 6 6-6" />
      </svg>
    </button>
  );
  const Heading = heading;
  return (
    <div className={className === undefined ? 'collapsible' : `collapsible ${className}`} data-open={open || undefined}>
      {Heading === undefined ? toggle : <Heading className="collapsible-heading">{toggle}</Heading>}
      <div id={panelId} className="collapsible-panel" inert={!open}>
        <div className="collapsible-body">{children}</div>
      </div>
    </div>
  );
}
