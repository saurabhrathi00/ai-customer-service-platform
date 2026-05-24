import { cn } from '@/lib/utils';

interface LogoProps {
  size?: number;
  className?: string;
  withWordmark?: boolean;
}

export function Logo({ size = 28, className, withWordmark = true }: LogoProps) {
  return (
    <div className={cn('flex items-center gap-2', className)}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 32 32"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden
      >
        <defs>
          <linearGradient id="lg" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
            <stop offset="0" stopColor="hsl(262 83% 58%)" />
            <stop offset="1" stopColor="hsl(214 90% 60%)" />
          </linearGradient>
        </defs>
        <rect x="2" y="2" width="28" height="28" rx="8" fill="url(#lg)" />
        <path
          d="M10 21V13a3 3 0 0 1 3-3h6a3 3 0 0 1 3 3v3a3 3 0 0 1-3 3h-3l-3 3v-3h-3z"
          fill="white"
          fillOpacity="0.95"
        />
      </svg>
      {withWordmark && (
        <span className="text-base font-semibold tracking-tight">
          Vox<span className="text-primary">AI</span>
        </span>
      )}
    </div>
  );
}
