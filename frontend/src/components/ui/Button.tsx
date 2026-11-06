import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';
import { cn } from './cn.ts';

/**
 * shadcn/ui's button (D-E8-12). One primary action per screen (`primary`, the accent fill); the rest are `secondary`
 * (a bordered surface) or `ghost`. Pressing scales it down a touch (Apple HIG feedback); never under reduced motion.
 */
export const buttonVariants = cva(
  [
    'inline-flex shrink-0 items-center justify-center gap-2 rounded-md text-sm font-semibold whitespace-nowrap no-underline',
    'cursor-pointer transition-[background-color,border-color,color,transform,box-shadow] duration-[var(--dur-fast)]',
    'motion-safe:active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60',
    'aria-disabled:cursor-progress aria-disabled:opacity-70',
    '[&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0',
  ],
  {
    variants: {
      variant: {
        primary: 'border border-transparent bg-primary text-primary-foreground hover:bg-primary-hover',
        secondary:
          'border border-border bg-card text-foreground shadow-card hover:border-input/50 hover:bg-muted',
        ghost: 'border border-transparent bg-transparent text-foreground hover:bg-muted',
        danger:
          'border border-border bg-card text-destructive-strong shadow-card hover:border-destructive hover:bg-destructive-soft',
        link: 'border-0 bg-transparent p-0 text-primary-text underline-offset-4 hover:underline',
      },
      size: {
        md: 'min-h-11 px-4',
        sm: 'min-h-9 px-3 text-[13px]',
        icon: 'size-11',
      },
    },
    defaultVariants: { variant: 'secondary', size: 'md' },
  },
);

type ButtonProps = ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    /** Renders its only child (a link, say) with the button's look, as shadcn's `asChild`. */
    asChild?: boolean;
  };

export function Button({ className, variant, size, asChild = false, type, ...props }: ButtonProps) {
  const Component = asChild ? Slot : 'button';
  return (
    <Component
      className={cn(buttonVariants({ variant, size }), className)}
      {...(asChild ? {} : { type: type ?? 'button' })}
      {...props}
    />
  );
}
