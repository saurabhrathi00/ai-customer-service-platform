import { Outlet, Link } from 'react-router-dom';
import { isAuthenticated } from '@/store/auth';
import { Logo } from './Logo';
import { Footer } from './Footer';

export function PublicLayout() {
  const loggedIn = isAuthenticated();

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground">
      <header className="border-b bg-background/80 backdrop-blur sticky top-0 z-30">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <Link to={loggedIn ? '/' : '/pricing'}>
            <Logo />
          </Link>
          <nav className="flex items-center gap-4 text-sm">
            <Link to="/about" className="font-medium hover:text-primary transition-colors">
              About
            </Link>
            <Link to="/pricing" className="font-medium hover:text-primary transition-colors">
              Pricing
            </Link>
            <Link to="/support" className="font-medium hover:text-primary transition-colors">
              Support
            </Link>
            {loggedIn ? (
              <Link to="/" className="font-medium hover:text-primary transition-colors">
                Dashboard
              </Link>
            ) : (
              <Link to="/login" className="font-medium hover:text-primary transition-colors">
                Login
              </Link>
            )}
          </nav>
        </div>
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <Footer />
    </div>
  );
}
