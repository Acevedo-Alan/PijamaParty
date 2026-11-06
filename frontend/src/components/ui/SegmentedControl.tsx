import * as ToggleGroup from '@radix-ui/react-toggle-group';
import type { CSSProperties, ReactNode } from 'react';
import { cn } from './cn.ts';

interface Option<T extends string> {
  value: T;
  label: string;
  /** A decorative icon before the label, e.g. the calendar of a custom range (hidden from screen readers). */
  icon?: ReactNode;
}

interface SegmentedControlProps<T extends string> {
  /** Names the group for screen readers, e.g. "Período". */
  label: string;
  options: readonly Option<T>[];
  value: T;
  onValueChange: (value: T) => void;
  className?: string;
}

/**
 * A segmented control (Apple HIG) on Radix's toggle group: one option is always chosen, the arrow keys move between
 * them, and a white marker slides under the chosen one (CSS `transform`, the motion tokens; instant under reduced motion).
 * Each option is a 44 px touch target (RNF-26). The chosen one stands out by weight and by its raised marker, not only
 * by its color (D-E8-12).
 */
export function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onValueChange,
  className,
}: SegmentedControlProps<T>) {
  const index = Math.max(0, options.findIndex((option) => option.value === value));
  const style = { '--segments': options.length, '--segment': index } as CSSProperties;
  return (
    <ToggleGroup.Root
      type="single"
      aria-label={label}
      value={value}
      onValueChange={(next) => {
        // Radix allows clicking the chosen one again to clear it; a segmented control always keeps one.
        const chosen = options.find((option) => option.value === next);
        if (chosen !== undefined) {
          onValueChange(chosen.value);
        }
      }}
      style={style}
      className={cn('relative inline-grid auto-cols-fr grid-flow-col rounded-md bg-muted p-[3px]', className)}
    >
      <span
        aria-hidden="true"
        className={cn(
          'pointer-events-none absolute inset-y-[3px] left-[3px] rounded-sm border border-input bg-card shadow-raised',
          'w-[calc((100%-6px)/var(--segments))] translate-x-[calc(100%*var(--segment))]',
          'motion-safe:transition-transform motion-safe:duration-[var(--dur-base)] motion-safe:ease-[var(--ease-out)]',
        )}
      />
      {options.map((option) => (
        <ToggleGroup.Item
          key={option.value}
          value={option.value}
          className={cn(
            'relative z-[1] inline-flex min-h-11 cursor-pointer items-center justify-center gap-1.5 rounded-sm border-0 bg-transparent px-2 text-[13px] font-medium whitespace-nowrap',
            'text-muted-foreground transition-colors duration-[var(--dur-fast)] data-[state=on]:font-bold data-[state=on]:text-primary-text',
            'focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-ring',
          )}
        >
          {option.icon}
          {option.label}
        </ToggleGroup.Item>
      ))}
    </ToggleGroup.Root>
  );
}
