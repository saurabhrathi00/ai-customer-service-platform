import { Outlet, Link } from 'react-router-dom';
import { Logo } from './Logo';

export function PublicLayout() {
  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground">
      <header className="border-b bg-background/80 backdrop-blur sticky top-0 z-30">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <Link to="/pricing">
            <Logo />
          </Link>
          <nav className="flex items-center gap-4 text-sm">
            <Link to="/pricing" className="font-medium hover:text-primary transition-colors">
              Pricing
            </Link>
            <Link to="/login" className="font-medium hover:text-primary transition-colors">
              Login
            </Link>
          </nav>
        </div>
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="border-t bg-card/40">
        <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          <div className="flex flex-col items-center gap-4 sm:flex-row sm:justify-between">
            <p className="text-sm text-muted-foreground">VoxHelperAI</p>
            <nav className="flex flex-wrap gap-4 text-sm text-muted-foreground">
              <Link to="/terms" className="hover:text-foreground transition-colors">Terms</Link>
              <Link to="/privacy" className="hover:text-foreground transition-colors">Privacy</Link>
              <Link to="/refund" className="hover:text-foreground transition-colors">Refund Policy</Link>
              <Link to="/support" className="hover:text-foreground transition-colors">Support</Link>
            </nav>
          </div>
        </div>
      </footer>
    </div>
  );
}
