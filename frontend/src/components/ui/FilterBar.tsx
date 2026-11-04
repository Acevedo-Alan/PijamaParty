import type { ComponentProps } from 'react';
import { cn } from './cn.ts';

/**
 * The filters above a back-office list (D-E8-12, T-E8-18): a search landmark whose fields wrap in a row, one per line at
 * 360 px. Compose it with Field parts and a Button; FilterBarActions keeps the buttons on the fields' baseline.
 *
 * <FilterBar aria-label="Buscar pedidos" onSubmit={...}>
 *   <Field><FieldLabel>Buscar</FieldLabel><FieldInput type="search" /></Field>
 *   <FilterBarActions><Button type="submit">Buscar</Button></FilterBarActions>
 * </FilterBar>
 */
export function FilterBar({ className, ...props }: ComponentProps<'form'>) {
  return (
    <form
      role="search"
      noValidate
      className={cn(
        'flex flex-col gap-3 rounded-xl border border-border bg-card p-4 shadow-card',
        'min-[640px]:flex-row min-[640px]:flex-wrap min-[640px]:items-start',
        className,
      )}
      {...props}
    />
  );
}

/** The buttons of a filter bar, below a label's height so they line up with the controls. */
export function FilterBarActions({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex gap-2 min-[640px]:pt-[26px]', className)} {...props} />;
}
