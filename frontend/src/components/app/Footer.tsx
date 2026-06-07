import { Link } from 'react-router-dom';
import { COMPANY } from '@/lib/constants';

const LINKS = [
  { to: '/about', label: 'About' },
  { to: '/pricing', label: 'Pricing' },
  { to: '/support', label: 'Support' },
  { to: '/terms', label: 'Terms' },
  { to: '/privacy', label: 'Privacy' },
  { to: '/refund', label: 'Refund Policy' },
] as const;

export function Footer() {
  return (
    <footer className="border-t bg-card/40">
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="flex flex-col gap-6 sm:flex-row sm:justify-between">
          <div>
            <p className="text-sm font-medium">{COMPANY.name}</p>
            <p className="mt-1 text-xs text-muted-foreground">{COMPANY.tagline}</p>
            <div className="mt-2 space-y-0.5 text-xs text-muted-foreground">
              <p>{COMPANY.phone}</p>
              <p>{COMPANY.email}</p>
            </div>
          </div>
          <nav className="flex flex-wrap gap-x-4 gap-y-2 text-sm text-muted-foreground">
            {LINKS.map((l) => (
              <Link key={l.to} to={l.to} className="hover:text-foreground transition-colors">
                {l.label}
              </Link>
            ))}
          </nav>
        </div>
        <p className="mt-6 text-xs text-muted-foreground">
          &copy; {new Date().getFullYear()} {COMPANY.name}. All rights reserved.
        </p>
      </div>
    </footer>
  );
}
