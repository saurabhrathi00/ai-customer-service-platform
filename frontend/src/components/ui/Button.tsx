import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

export const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-all duration-200 disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
  {
    variants: {
      variant: {
        default:
          'relative overflow-hidden shiny ' +
          'bg-primary text-primary-foreground ' +
          'shadow-[0_0_16px_hsl(var(--primary)/0.50),0_2px_6px_hsl(var(--primary)/0.25)] ' +
          'hover:brightness-110 hover:shadow-[0_0_28px_hsl(var(--primary)/0.65),0_4px_14px_hsl(var(--primary)/0.35)] ' +
          'active:scale-[0.97] active:brightness-95',
        outline:
          'border border-input bg-transparent ' +
          'hover:bg-accent hover:text-accent-foreground ' +
          'hover:border-primary/35 hover:shadow-[0_0_12px_hsl(var(--primary)/0.18)]',
        ghost: 'hover:bg-accent hover:text-accent-foreground',
        secondary:
          'bg-secondary text-secondary-foreground ' +
          'hover:bg-secondary/80 hover:shadow-sm',
        destructive:
          'bg-destructive text-destructive-foreground shiny ' +
          'shadow-[0_0_12px_hsl(var(--destructive)/0.35)] ' +
          'hover:shadow-[0_0_20px_hsl(var(--destructive)/0.50)] hover:brightness-110',
        link: 'text-primary underline-offset-4 hover:underline',
      },
      size: {
        default: 'h-10 px-4 py-2',
        sm: 'h-9 px-3 text-sm',
        lg: 'h-11 px-6 text-base font-semibold',
        icon: 'h-10 w-10',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  loading?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, loading, disabled, children, ...props }, ref) => (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    >
      {loading && (
        <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-r-transparent" />
      )}
      {children}
    </button>
  ),
);
Button.displayName = 'Button';
