import { useId, useState, type ReactNode } from 'react';
import './Collapsible.css';

interface CollapsibleProps {
  title: string;
  className?: string;
  children: ReactNode;
}

/**
 * A collapsed section that slides open and closed (E7 motion, `grid-template-rows: 0fr → 1fr`). While closed its
 * content is inert: out of the tab order and hidden from screen readers.
 */
export function Collapsible({ title, className, children }: CollapsibleProps) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  return (
    <div className={className === undefined ? 'collapsible' : `collapsible ${className}`}>
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
      <div id={panelId} className="collapsible-panel" inert={!open}>
        <div className="collapsible-body">{children}</div>
      </div>
    </div>
  );
}
