import type { ReactNode } from 'react';
import { cn } from './cn.ts';

interface EmptyStateProps {
  title: string;
  children?: ReactNode;
  /** A decorative icon above the title. */
  icon?: ReactNode;
  className?: string;
}

/** What a block shows before it has anything (D-E8-12): an icon, a short title and one line on what will appear. */
export function EmptyState({ title, children, icon, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-border px-6 py-10',
        'text-center',
        className,
      )}
    >
      {icon !== undefined && (
        <span className="text-primary [&_svg]:size-7" aria-hidden="true">
          {icon}
        </span>
      )}
      <p className="m-0 text-[15px] font-bold text-foreground">{title}</p>
      {children !== undefined && <div className="max-w-sm text-[13px] text-muted-foreground">{children}</div>}
    </div>
  );
}
