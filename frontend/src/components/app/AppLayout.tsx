import * as React from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
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
  Zap,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { Logo } from './Logo';
import { Footer } from './Footer';
import { SessionExpiryGuard } from './SessionExpiryGuard';
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
  const [collapsed, setCollapsed] = React.useState(() =>
    localStorage.getItem('sidebar-collapsed') === 'true'
  );
  const navigate = useNavigate();
  const { businessId, email, logout } = useAuthStore();
  const { data: biz } = useQuery({
    queryKey: ['business', businessId],
    queryFn: () => business.profile(businessId!),
    enabled: Boolean(businessId),
    staleTime: 5 * 60_000,
  });
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

  function toggleCollapsed() {
    const next = !collapsed;
    setCollapsed(next);
    localStorage.setItem('sidebar-collapsed', String(next));
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <SessionExpiryGuard />

      {/* Mobile top bar */}
      <header className="lg:hidden sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border/50 bg-background/80 backdrop-blur-xl px-4">
        <Logo />
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => navigate('/leads')}
            aria-label={`${pendingLeadCount ?? 0} pending leads`}
            className="relative rounded-lg p-2 hover:bg-accent transition-colors"
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
            className="rounded-lg p-2 hover:bg-accent transition-colors"
          >
            <Menu className="h-5 w-5" />
          </button>
        </div>
      </header>

      <div className="flex">
        {/* Sidebar (desktop) */}
        <motion.aside
          animate={{ width: collapsed ? 56 : 240 }}
          transition={{ duration: 0.22, ease: 'easeInOut' }}
          className="hidden lg:flex sticky top-0 h-screen flex-col bg-card/40 backdrop-blur-2xl sidebar-glow border-r border-border/50 overflow-hidden"
        >
          <SidebarBody
            businessName={biz?.name}
            email={email}
            onLogout={doLogout}
            pendingLeads={pendingLeadCount ?? 0}
            collapsed={collapsed}
            onToggle={toggleCollapsed}
          />
        </motion.aside>

        {/* Sidebar drawer (mobile) */}
        <AnimatePresence>
          {mobileOpen && (
            <motion.div
              className="fixed inset-0 z-40 lg:hidden"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
            >
              <div
                className="absolute inset-0 bg-black/70 backdrop-blur-sm"
                onClick={() => setMobileOpen(false)}
              />
              <motion.aside
                className="relative h-full w-72 max-w-[85vw] border-r border-border/50 bg-card shadow-2xl"
                initial={{ x: -280 }}
                animate={{ x: 0 }}
                exit={{ x: -280 }}
                transition={{ type: 'spring', damping: 30, stiffness: 300 }}
              >
                <button
                  aria-label="Close menu"
                  onClick={() => setMobileOpen(false)}
                  className="absolute right-3 top-3 rounded-md p-1.5 hover:bg-accent transition-colors"
                >
                  <X className="h-4 w-4" />
                </button>
                <SidebarBody
                  businessName={biz?.name}
                  email={email}
                  onLogout={doLogout}
                  pendingLeads={pendingLeadCount ?? 0}
                  onNavigate={() => setMobileOpen(false)}
                  collapsed={false}
                  onToggle={() => {}}
                />
              </motion.aside>
            </motion.div>
          )}
        </AnimatePresence>

        <main className="flex-1 min-w-0 flex flex-col">
          <div className="flex-1">
            <Outlet />
          </div>
          <Footer />
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
  collapsed,
  onToggle,
}: {
  businessName?: string;
  email: string | null;
  onLogout: () => void;
  onNavigate?: () => void;
  pendingLeads: number;
  collapsed: boolean;
  onToggle: () => void;
}) {
  const { theme, toggle } = useTheme();

  return (
    <div className="flex h-full flex-col">
      {/* Logo */}
      <div className={cn(
        'flex h-14 items-center border-b border-border/40 shrink-0',
        collapsed ? 'justify-center px-0' : 'px-5',
      )}>
        {collapsed
          ? <Logo size={24} withWordmark={false} />
          : <Logo />
        }
      </div>

      {/* Nav items */}
      <nav className="flex-1 px-2 py-4 overflow-y-auto">
        <ul className="space-y-0.5">
          {NAV.map((item, i) => {
            const Icon = item.icon;
            return (
              <motion.li
                key={item.to}
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.04, duration: 0.22, ease: 'easeOut' }}
              >
                <NavLink
                  to={item.to}
                  end={item.end}
                  onClick={onNavigate}
                  title={collapsed ? item.label : undefined}
                  className={({ isActive }) =>
                    cn(
                      'group relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-150',
                      collapsed && 'justify-center px-0',
                      isActive
                        ? 'bg-primary/10 text-primary'
                        : 'text-muted-foreground hover:bg-accent/60 hover:text-foreground',
                    )
                  }
                >
                  {({ isActive }) => (
                    <>
                      {/* Left accent bar */}
                      {isActive && (
                        <motion.div
                          layoutId="activeBar"
                          className="absolute left-0 top-1/2 -translate-y-1/2 h-5 w-0.5 rounded-full bg-primary"
                          style={{ boxShadow: '0 0 8px hsl(252 90% 67% / 0.8)' }}
                          transition={{ type: 'spring', stiffness: 500, damping: 40 }}
                        />
                      )}
                      <span className={cn(
                        'flex h-7 w-7 shrink-0 items-center justify-center rounded-md transition-all duration-150',
                        isActive
                          ? 'bg-primary/15 text-primary'
                          : 'text-muted-foreground group-hover:text-foreground',
                      )}>
                        <Icon className="h-4 w-4" />
                      </span>
                      {!collapsed && (
                        <>
                          <span className="flex-1 truncate">{item.label}</span>
                          {item.to === '/leads' && pendingLeads > 0 && (
                            <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-[10px] font-bold text-primary-foreground shadow-[0_0_8px_hsl(var(--primary)/0.5)]">
                              {pendingLeads}
                            </span>
                          )}
                        </>
                      )}
                    </>
                  )}
                </NavLink>
              </motion.li>
            );
          })}

          {/* Toggle button */}
          <li className="flex justify-end pr-2 pt-1">
            <button
              onClick={onToggle}
              title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              className="flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground hover:bg-accent/60 hover:text-foreground transition-all duration-150"
            >
              {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
            </button>
          </li>
        </ul>
      </nav>

      {/* AI status */}
      <div className="px-3 pb-2 shrink-0">
        {collapsed ? (
          <div className="flex items-center justify-center py-2.5">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-60" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-success" />
            </span>
          </div>
        ) : (
          <div className="flex items-center gap-2 rounded-lg border border-success/20 bg-success/5 px-3 py-2.5">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-60" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-success" />
            </span>
            <Zap className="h-3.5 w-3.5 text-success" />
            <span className="text-xs font-semibold text-success">AI Agent Active</span>
          </div>
        )}
      </div>

      {/* User section */}
      <div className="border-t border-border/40 p-3 space-y-1 shrink-0">
        {collapsed ? (
          <div className="flex items-center justify-center py-1">
            <div
              className="relative grid h-8 w-8 shrink-0 place-items-center rounded-full bg-primary/15 text-primary text-xs font-bold ring-1 ring-primary/25"
              style={{ boxShadow: '0 0 10px hsl(252 90% 67% / 0.20)' }}
              title={businessName ?? email ?? ''}
            >
              {initials(businessName ?? email ?? '?')}
            </div>
          </div>
        ) : (
          <>
            <div className="flex items-center gap-3 rounded-lg px-2 py-2">
              <div
                className="relative grid h-8 w-8 shrink-0 place-items-center rounded-full bg-primary/15 text-primary text-xs font-bold ring-1 ring-primary/25"
                style={{ boxShadow: '0 0 10px hsl(252 90% 67% / 0.20)' }}
              >
                {initials(businessName ?? email ?? '?')}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium leading-tight">{businessName ?? 'Your business'}</p>
                <p className="truncate text-xs text-muted-foreground">{email ?? '—'}</p>
              </div>
            </div>
            <div className="flex gap-1">
              <Button
                variant="ghost"
                size="sm"
                className="flex-1 justify-start gap-2 text-muted-foreground hover:text-foreground text-xs"
                onClick={toggle}
              >
                {theme === 'dark' ? <Sun className="h-3.5 w-3.5" /> : <Moon className="h-3.5 w-3.5" />}
                {theme === 'dark' ? 'Light' : 'Dark'}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className="flex-1 justify-start gap-2 text-destructive hover:text-destructive text-xs"
                onClick={onLogout}
              >
                <LogOut className="h-3.5 w-3.5" />
                Logout
              </Button>
            </div>
          </>
        )}
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
    <div className="flex flex-col gap-3 border-b border-border/40 px-6 py-6 sm:flex-row sm:items-center sm:justify-between lg:px-10">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-0.5 text-sm text-muted-foreground">{subtitle}</p>}
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
