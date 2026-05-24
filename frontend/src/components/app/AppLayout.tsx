import * as React from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  PhoneCall,
  BookOpen,
  Bell,
  Sliders,
  Settings,
  Moon,
  Sun,
  LogOut,
  Menu,
  X,
} from 'lucide-react';
import { Logo } from './Logo';
import { useTheme } from './ThemeProvider';
import { useAuthStore } from '@/store/auth';
import { Button } from '@/components/ui/Button';
import { cn, initials } from '@/lib/utils';
import { useQuery } from '@tanstack/react-query';
import { business, leads } from '@/api/resources';

const NAV: { to: string; label: string; icon: React.ComponentType<{ className?: string }>; end?: boolean }[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/leads', label: 'Leads', icon: Bell },
  { to: '/calls', label: 'Calls', icon: PhoneCall },
  { to: '/knowledge', label: 'Knowledge', icon: BookOpen },
  { to: '/configurations', label: 'Configurations', icon: Sliders },
  { to: '/settings', label: 'Settings', icon: Settings },
];

export function AppLayout() {
  const [mobileOpen, setMobileOpen] = React.useState(false);
  const navigate = useNavigate();
  const { businessId, email, logout } = useAuthStore();
  const { data: biz } = useQuery({
    queryKey: ['business', businessId],
    queryFn: () => business.profile(businessId!),
    enabled: Boolean(businessId),
    staleTime: 5 * 60_000,
  });
  // Pending lead count powers the sidebar badge + topbar bell. Cheap query
  // keyed on businessId so all consumers share it.
  const { data: pendingLeadCount } = useQuery({
    queryKey: ['leads', businessId, 'pending-count'],
    queryFn: async () => {
      const all = await leads.list(businessId!);
      return all.filter((l) => l.status === 'NEW').length;
    },
    enabled: Boolean(businessId),
    refetchInterval: 30_000,
  });

  function doLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* Mobile top bar */}
      <header className="lg:hidden sticky top-0 z-30 flex h-14 items-center justify-between border-b bg-background/80 backdrop-blur px-4">
        <Logo />
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => navigate('/leads')}
            aria-label={`${pendingLeadCount ?? 0} pending leads`}
            className="relative rounded-md p-2 hover:bg-accent"
          >
            <Bell className="h-5 w-5" />
            {(pendingLeadCount ?? 0) > 0 && (
              <span className="absolute right-1 top-1 inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold text-primary-foreground">
                {pendingLeadCount}
              </span>
            )}
          </button>
          <button
            aria-label="Open menu"
            onClick={() => setMobileOpen(true)}
            className="rounded-md p-2 hover:bg-accent"
          >
            <Menu className="h-5 w-5" />
          </button>
        </div>
      </header>

      <div className="flex">
        {/* Sidebar (desktop) */}
        <aside className="hidden lg:flex sticky top-0 h-screen w-64 flex-col border-r bg-card/40 backdrop-blur">
          <SidebarBody businessName={biz?.name} email={email} onLogout={doLogout}
                       pendingLeads={pendingLeadCount ?? 0} />
        </aside>

        {/* Sidebar drawer (mobile) */}
        {mobileOpen && (
          <div className="fixed inset-0 z-40 lg:hidden animate-fade-in">
            <div
              className="absolute inset-0 bg-black/50 backdrop-blur-sm"
              onClick={() => setMobileOpen(false)}
            />
            <aside className="relative h-full w-72 max-w-[85vw] border-r bg-card shadow-xl animate-slide-up">
              <button
                aria-label="Close menu"
                onClick={() => setMobileOpen(false)}
                className="absolute right-3 top-3 rounded-md p-1.5 hover:bg-accent"
              >
                <X className="h-4 w-4" />
              </button>
              <SidebarBody
                businessName={biz?.name}
                email={email}
                onLogout={doLogout}
                pendingLeads={pendingLeadCount ?? 0}
                onNavigate={() => setMobileOpen(false)}
              />
            </aside>
          </div>
        )}

        <main className="flex-1 min-w-0">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function SidebarBody({
  businessName,
  email,
  onLogout,
  onNavigate,
  pendingLeads,
}: {
  businessName?: string;
  email: string | null;
  onLogout: () => void;
  onNavigate?: () => void;
  pendingLeads: number;
}) {
  const { theme, toggle } = useTheme();
  return (
    <div className="flex h-full flex-col">
      <div className="flex h-16 items-center px-6">
        <Logo />
      </div>

      <nav className="flex-1 px-3 py-2">
        <ul className="space-y-1">
          {NAV.map((item) => {
            const Icon = item.icon;
            return (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                      'hover:bg-accent hover:text-accent-foreground',
                      isActive && 'bg-accent text-accent-foreground',
                    )
                  }
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  <span className="flex-1">{item.label}</span>
                  {item.to === '/leads' && pendingLeads > 0 && (
                    <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-[10px] font-semibold text-primary-foreground">
                      {pendingLeads}
                    </span>
                  )}
                </NavLink>
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="border-t p-3">
        <div className="flex items-center gap-3 rounded-md p-2">
          <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-primary/10 text-primary text-sm font-semibold">
            {initials(businessName ?? email ?? '?')}
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{businessName ?? 'Your business'}</p>
            <p className="truncate text-xs text-muted-foreground">{email ?? '—'}</p>
          </div>
        </div>
        <div className="mt-2 flex gap-1">
          <Button variant="ghost" size="sm" className="flex-1 justify-start" onClick={toggle}>
            {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            {theme === 'dark' ? 'Light' : 'Dark'}
          </Button>
          <Button variant="ghost" size="sm" className="flex-1 justify-start text-destructive hover:text-destructive" onClick={onLogout}>
            <LogOut className="h-4 w-4" />
            Logout
          </Button>
        </div>
      </div>
    </div>
  );
}

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3 border-b px-6 py-6 sm:flex-row sm:items-center sm:justify-between lg:px-10">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function PageBody({
  children,
  className,
}: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('px-6 py-6 lg:px-10', className)}>{children}</div>;
}
