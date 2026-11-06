import { esAdmin } from '../../i18n/esAdmin.ts';
import { cn } from './cn.ts';

/** A grey block that stands where content will arrive (D-E8-12); it pulses unless the person asked for less motion. */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn('rounded-md bg-muted motion-safe:animate-pulse', className)} />;
}

/**
 * What a back-office list shows while its first page loads (T-E8-26): rows of the list's size, so nothing jumps when
 * the data arrives. Screen readers get the same "Cargando…" status as before.
 */
export function ListSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div role="status" aria-busy="true">
      <span className="visually-hidden">{esAdmin.loading}</span>
      <ul aria-hidden="true" className="m-0 grid list-none gap-2 p-0">
        {Array.from({ length: rows }, (_, row) => (
          <li key={row} className="flex flex-col gap-2 rounded-lg border border-border bg-card p-3">
            <Skeleton className="h-4 w-2/5" />
            <Skeleton className="h-3 w-3/5" />
            <Skeleton className="h-3 w-1/4" />
          </li>
        ))}
      </ul>
    </div>
  );
}
