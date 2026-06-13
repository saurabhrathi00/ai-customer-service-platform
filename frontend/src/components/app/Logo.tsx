import { cn } from '@/lib/utils';

interface LogoProps {
  size?: number;
  className?: string;
  withWordmark?: boolean;
}

export function Logo({ size = 28, className, withWordmark = true }: LogoProps) {
  return (
    <div className={cn('flex items-center gap-2', className)}>
      <div className="relative" style={{ width: size, height: size }}>
        {/* Glow halo */}
        <div
          className="absolute inset-0 rounded-xl bg-primary/40 blur-md animate-glow-pulse"
          style={{ borderRadius: size * 0.3 }}
        />
        <svg
          width={size}
          height={size}
          viewBox="0 0 32 32"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden
          className="relative z-10"
        >
          <defs>
            <linearGradient id="logoGrad" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
              <stop offset="0"   stopColor="hsl(8 80% 68%)" />
              <stop offset="0.5" stopColor="hsl(15 82% 62%)" />
              <stop offset="1"   stopColor="hsl(350 65% 58%)" />
            </linearGradient>
          </defs>
          <rect x="2" y="2" width="28" height="28" rx="8" fill="url(#logoGrad)" />
          <path
            d="M10 21V13a3 3 0 0 1 3-3h6a3 3 0 0 1 3 3v3a3 3 0 0 1-3 3h-3l-3 3v-3h-3z"
            fill="white"
            fillOpacity="0.95"
          />
        </svg>
      </div>
      {withWordmark && (
        <span className="text-base font-bold tracking-tight">
          Vox<span className="gradient-text">AI</span>
        </span>
      )}
    </div>
  );
}
