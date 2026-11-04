import { createContext, use, useId, type ComponentProps, type ReactNode } from 'react';
import { cn } from './cn.ts';

/** What the parts of one field share: its control's id, and whether it shows a hint and an error. */
interface FieldState {
  id: string;
  hintId: string | null;
  errorId: string | null;
}

const FieldContext = createContext<FieldState | null>(null);

function useField(part: string): FieldState {
  const field = use(FieldContext);
  if (field === null) {
    throw new Error(`${part} must be inside a Field`);
  }
  return field;
}

interface FieldProps {
  /** The text under the control, always shown. */
  hint?: ReactNode;
  /** Shown under the hint when set; the control is then marked invalid. */
  error?: ReactNode;
  className?: string;
  children: ReactNode;
}

/**
 * A label, its control, a hint and an error (D-E8-12, T-E8-18), composed of its parts rather than configured with flags:
 * the parts link the label, the hint and the error to the control by id, so a screen reader reads them with it.
 *
 * <Field hint="..." error={invalid ? '...' : undefined}>
 *   <FieldLabel>Email</FieldLabel>
 *   <FieldInput type="email" value={...} onChange={...} />
 * </Field>
 */
export function Field({ hint, error, className, children }: FieldProps) {
  const id = useId();
  const state: FieldState = {
    id,
    hintId: hint === undefined ? null : `${id}-hint`,
    errorId: error === undefined || error === null || error === false ? null : `${id}-error`,
  };
  return (
    <FieldContext value={state}>
      <div className={cn('flex min-w-0 flex-col gap-1.5', className)}>
        {children}
        {state.hintId !== null && (
          <p id={state.hintId} className="m-0 text-[13px] text-muted-foreground">
            {hint}
          </p>
        )}
        {state.errorId !== null && (
          <p id={state.errorId} className="m-0 text-[13px] font-semibold text-destructive-strong">
            {error}
          </p>
        )}
      </div>
    </FieldContext>
  );
}

export function FieldLabel({ className, ...props }: Omit<ComponentProps<'label'>, 'htmlFor'>) {
  const { id } = useField('FieldLabel');
  return <label htmlFor={id} className={cn('text-[13px] font-semibold text-foreground', className)} {...props} />;
}

/** The control's look (D-E8-12): 44 px tall, a fine border, red when invalid. */
export const FIELD_CONTROL =
  'min-h-11 w-full min-w-0 rounded-md border border-input bg-card px-3 text-[15px] text-foreground ' +
  'aria-invalid:border-destructive';

/** The props that link a control to its label, hint and error; for a control other than {@link FieldInput}. */
export function useFieldControl(part = 'The control') {
  const { id, hintId, errorId } = useField(part);
  const describedBy = [hintId, errorId].filter((value) => value !== null).join(' ');
  return {
    id,
    'aria-invalid': errorId !== null,
    ...(describedBy === '' ? {} : { 'aria-describedby': describedBy }),
  };
}

export function FieldInput({ className, ...props }: Omit<ComponentProps<'input'>, 'id'>) {
  const control = useFieldControl('FieldInput');
  return <input className={cn(FIELD_CONTROL, className)} {...control} {...props} />;
}

export function FieldSelect({ className, ...props }: Omit<ComponentProps<'select'>, 'id'>) {
  const control = useFieldControl('FieldSelect');
  return <select className={cn(FIELD_CONTROL, 'pr-8', className)} {...control} {...props} />;
}
