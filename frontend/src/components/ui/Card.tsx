import type { ComponentProps } from 'react';
import { cn } from './cn.ts';

/**
 * shadcn/ui's card (D-E8-12), composed of its parts rather than configured with flags. A card is the first plane over
 * the cream base (Apple HIG depth): a white surface, a fine border and a light warm shadow.
 */
export function Card({ className, ...props }: ComponentProps<'section'>) {
  return (
    <section
      className={cn('flex min-w-0 flex-col gap-4 rounded-xl border border-border bg-card p-5 shadow-card', className)}
      {...props}
    />
  );
}

/** The title row: the title and its description on the left, an action (a link, a switch) on the right. */
export function CardHeader({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex flex-wrap items-start justify-between gap-3', className)} {...props} />;
}

export function CardTitle({ className, ...props }: ComponentProps<'h2'>) {
  return <h2 className={cn('m-0 text-base font-bold leading-snug text-foreground', className)} {...props} />;
}

export function CardDescription({ className, ...props }: ComponentProps<'p'>) {
  return <p className={cn('m-0 text-[13px] text-muted-foreground', className)} {...props} />;
}

export function CardContent({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex min-w-0 flex-col gap-3', className)} {...props} />;
}
