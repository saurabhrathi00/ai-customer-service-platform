import * as React from 'react';
import { Outlet, Link } from 'react-router-dom';
import { motion, useMotionValue, useSpring } from 'framer-motion';
import { isAuthenticated } from '@/store/auth';
import { Logo } from './Logo';
import { Footer } from './Footer';
import { CursorTrail } from '@/components/ui/CursorTrail';

export function PublicLayout() {
  const loggedIn = isAuthenticated();

  // ── Custom cursor ──────────────────────────────────────
  const cursorX = useMotionValue(-200);
  const cursorY = useMotionValue(-200);
  const dotX  = useSpring(cursorX, { stiffness: 700, damping: 40, mass: 0.4 });
  const dotY  = useSpring(cursorY, { stiffness: 700, damping: 40, mass: 0.4 });
  const ringX = useSpring(cursorX, { stiffness: 220, damping: 30, mass: 0.7 });
  const ringY = useSpring(cursorY, { stiffness: 220, damping: 30, mass: 0.7 });
  const glowX = useSpring(cursorX, { stiffness: 70,  damping: 22, mass: 1   });
  const glowY = useSpring(cursorY, { stiffness: 70,  damping: 22, mass: 1   });

  React.useEffect(() => {
    const onMove = (e: MouseEvent) => {
      cursorX.set(e.clientX);
      cursorY.set(e.clientY);
    };
    window.addEventListener('mousemove', onMove);
    return () => window.removeEventListener('mousemove', onMove);
  }, [cursorX, cursorY]);

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground lg:cursor-none">

      {/* ── Cursor trail (desktop only) ── */}
      <CursorTrail />

      {/* dot */}
      <motion.div
        className="pointer-events-none fixed z-[200] hidden lg:block h-2.5 w-2.5 rounded-full bg-primary"
        style={{ left: dotX, top: dotY, translateX: '-50%', translateY: '-50%' }}
      />
      {/* ring */}
      <motion.div
        className="pointer-events-none fixed z-[199] hidden lg:block h-7 w-7 rounded-full border border-primary/60"
        style={{ left: ringX, top: ringY, translateX: '-50%', translateY: '-50%' }}
      />
      {/* trailing glow */}
      <motion.div
        className="pointer-events-none fixed z-[198] hidden lg:block h-24 w-24 rounded-full bg-primary/10 blur-2xl"
        style={{ left: glowX, top: glowY, translateX: '-50%', translateY: '-50%' }}
      />

      <header className="border-b border-border/40 bg-background/80 backdrop-blur sticky top-0 z-30">
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
