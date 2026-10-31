import * as SwitchPrimitive from '@radix-ui/react-switch';
import type { ComponentProps } from 'react';
import { cn } from './cn.ts';

/** shadcn/ui's switch (D-E8-12), on Radix: a real `role="switch"` button, its thumb sliding on CSS `transform`. */
export function Switch({ className, ...props }: ComponentProps<typeof SwitchPrimitive.Root>) {
  return (
    <SwitchPrimitive.Root
      className={cn(
        'relative inline-flex h-6 w-10 shrink-0 cursor-pointer items-center rounded-full border border-input/60 p-0.5',
        'bg-muted transition-colors data-[state=checked]:border-primary data-[state=checked]:bg-primary',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb
        className={cn(
          'block size-[18px] rounded-full bg-card shadow-card data-[state=checked]:translate-x-4',
          'motion-safe:transition-transform motion-safe:duration-[var(--dur-fast)]',
        )}
      />
    </SwitchPrimitive.Root>
  );
}
