import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors',
  {
    variants: {
      variant: {
        default: 'bg-primary/10 text-primary border border-primary/20',
        secondary: 'bg-secondary text-secondary-foreground border border-transparent',
        outline: 'border border-border text-foreground',
        success: 'bg-success/10 text-[hsl(var(--success))] border border-[hsl(var(--success))]/20',
        warning: 'bg-warning/10 text-[hsl(var(--warning))] border border-[hsl(var(--warning))]/30',
        destructive: 'bg-destructive/10 text-destructive border border-destructive/20',
      },
    },
    defaultVariants: { variant: 'default' },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant, className }))} {...props} />;
}
