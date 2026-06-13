import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Eye, EyeOff, ArrowRight, Sparkles, Brain, PhoneCall, Star, Shield } from 'lucide-react';
import * as React from 'react';
import {
  motion,
  useMotionValue,
  useMotionTemplate,
  useSpring,
  useTransform,
} from 'framer-motion';

import { Logo } from '@/components/app/Logo';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { CursorTrail } from '@/components/ui/CursorTrail';
import { auth } from '@/api/resources';
import { isAuthenticated, useAuthStore } from '@/store/auth';

const schema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
});

type Values = z.infer<typeof schema>;

const FEATURES = [
  { icon: Brain,     text: 'Multilingual STT + natural voice' },
  { icon: Star,      text: 'Auto-summaries and interest scoring' },
  { icon: PhoneCall, text: 'Callback queue, ready in minutes' },
  { icon: Shield,    text: 'Strict per-business data isolation' },
];

const STATS = [
  { value: '24/7', label: 'Availability' },
  { value: '< 1s',  label: 'Response time' },
  { value: '99.9%', label: 'Uptime' },
];

export default function LoginPage() {
  const navigate  = useNavigate();
  const location  = useLocation();
  const from      = (location.state as { from?: string })?.from ?? '/';
  const setTokens = useAuthStore((s) => s.setTokens);

  if (isAuthenticated()) return <Navigate to={from} replace />;

  const [showPwd, setShowPwd] = React.useState(false);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  });
  const [error, setError] = React.useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (v: Values) => auth.signin(v.email, v.password),
    onSuccess: (data) => {
      setError(null);
      setTokens(data.token, data.refreshToken);
      toast.success('Welcome back');
      navigate(from, { replace: true });
    },
    onError: (err: any) => {
      const msg =
        err?.response?.data?.message ??
        err?.response?.data?.error ??
        err?.message ??
        'Sign in failed. Please try again.';
      setError(msg);
    },
  });

  // ── Custom cursor ──────────────────────────────────────────────
  const cursorX = useMotionValue(-200);
  const cursorY = useMotionValue(-200);
  const dotX  = useSpring(cursorX, { stiffness: 700, damping: 40, mass: 0.4 });
  const dotY  = useSpring(cursorY, { stiffness: 700, damping: 40, mass: 0.4 });
  const ringX = useSpring(cursorX, { stiffness: 550, damping: 38, mass: 0.5 });
  const ringY = useSpring(cursorY, { stiffness: 550, damping: 38, mass: 0.5 });
  const glowX = useSpring(cursorX, { stiffness: 70,  damping: 22, mass: 1 });
  const glowY = useSpring(cursorY, { stiffness: 70,  damping: 22, mass: 1 });

  React.useEffect(() => {
    const onMove = (e: MouseEvent) => { cursorX.set(e.clientX); cursorY.set(e.clientY); };
    window.addEventListener('mousemove', onMove);
    return () => window.removeEventListener('mousemove', onMove);
  }, [cursorX, cursorY]);

  // ── Left panel spotlight + parallax ──────────────────────────
  const panelX = useMotionValue(300);
  const panelY = useMotionValue(400);
  const panelSpringX = useSpring(panelX, { stiffness: 130, damping: 28 });
  const panelSpringY = useSpring(panelY, { stiffness: 130, damping: 28 });
  const spotlight = useMotionTemplate`radial-gradient(520px circle at ${panelSpringX}px ${panelSpringY}px, hsl(252 90% 67% / 0.18), transparent 62%)`;

  const orb1X = useTransform(panelSpringX, [0, 640], [16, -16]);
  const orb1Y = useTransform(panelSpringY, [0, 900], [16, -16]);
  const orb2X = useTransform(panelSpringX, [0, 640], [-20, 20]);
  const orb2Y = useTransform(panelSpringY, [0, 900], [-12, 12]);

  function onPanelMove(e: React.MouseEvent<HTMLDivElement>) {
    const r = e.currentTarget.getBoundingClientRect();
    panelX.set(e.clientX - r.left);
    panelY.set(e.clientY - r.top);
  }

  // ── Form 3-D tilt ─────────────────────────────────────────────
  const formNormX = useMotionValue(0.5);
  const formNormY = useMotionValue(0.5);
  const tiltX = useSpring(useTransform(formNormY, [0, 1], [3, -3]), { stiffness: 130, damping: 28 });
  const tiltY = useSpring(useTransform(formNormX, [0, 1], [-3, 3]), { stiffness: 130, damping: 28 });

  function onFormMove(e: React.MouseEvent<HTMLDivElement>) {
    const r = e.currentTarget.getBoundingClientRect();
    formNormX.set((e.clientX - r.left) / r.width);
    formNormY.set((e.clientY - r.top) / r.height);
  }
  function onFormLeave() { formNormX.set(0.5); formNormY.set(0.5); }

  return (
    <div className="min-h-screen w-full bg-background overflow-hidden lg:cursor-none">

      {/* ── Top nav ── */}
      <header className="fixed top-0 inset-x-0 z-[190] flex items-center justify-between px-6 py-3 border-b border-border/30 bg-background/80 backdrop-blur-md">
        <Logo />
        <nav className="hidden sm:flex items-center gap-1">
          {[{ label: 'About', to: '/about' }, { label: 'Pricing', to: '/pricing' }, { label: 'Support', to: '/support' }]
            .map(({ label, to }) => (
              <Link key={to} to={to}
                className="px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground rounded-md hover:bg-accent/50 transition-colors">
                {label}
              </Link>
            ))}
        </nav>
        <Link to="/register">
          <Button size="sm" className="gap-1.5">Get started <ArrowRight className="h-3.5 w-3.5" /></Button>
        </Link>
      </header>

      <CursorTrail />

      {/* Custom cursor */}
      <motion.div className="pointer-events-none fixed z-[200] hidden lg:block h-2.5 w-2.5 rounded-full bg-primary"
        style={{ left: dotX, top: dotY, translateX: '-50%', translateY: '-50%',
          boxShadow: '0 0 10px hsl(252 90% 67% / 0.8)' }} />
      <motion.div className="pointer-events-none fixed z-[199] hidden lg:block h-7 w-7 rounded-full border border-primary/60"
        style={{ left: ringX, top: ringY, translateX: '-50%', translateY: '-50%' }} />
      <motion.div className="pointer-events-none fixed z-[198] hidden lg:block h-28 w-28 rounded-full bg-primary/8 blur-2xl"
        style={{ left: glowX, top: glowY, translateX: '-50%', translateY: '-50%' }} />

      <div className="grid min-h-screen lg:grid-cols-2 pt-[57px]">

        {/* ── Left: marketing ── */}
        <div
          className="relative hidden lg:flex flex-col justify-between px-12 py-10 border-r border-border/40 overflow-hidden"
          onMouseMove={onPanelMove}
        >
          <div className="absolute inset-0 aurora-bg" />
          <div className="absolute inset-0 dot-grid opacity-50" />
          <motion.div className="pointer-events-none absolute inset-0 z-[5]" style={{ background: spotlight }} />

          {/* Parallax orbs */}
          <motion.div className="absolute top-1/4 left-1/4 h-72 w-72 rounded-full bg-primary/8 blur-3xl" style={{ x: orb1X, y: orb1Y }} />
          <motion.div className="absolute bottom-1/3 right-1/4 h-52 w-52 rounded-full bg-sky-500/6 blur-3xl" style={{ x: orb2X, y: orb2Y }} />

          {/* Hero content */}
          <div className="relative z-10 space-y-8">
            <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, ease: 'easeOut' }}>
              <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-primary/25 bg-primary/8 px-3 py-1.5 text-xs font-semibold text-primary">
                <Sparkles className="h-3 w-3" />
                Powered by Gemini AI
              </div>
              <h1 className="text-[2.6rem] font-bold leading-[1.15] tracking-[-0.03em]">
                Your AI voice agent,
                <br />
                <span className="gradient-text">handling every call.</span>
              </h1>
              <p className="mt-4 text-[0.95rem] text-muted-foreground leading-relaxed max-w-sm">
                VoxAI answers your business calls 24/7, summarises every conversation,
                and surfaces the leads worth calling back.
              </p>
            </motion.div>

            {/* AI orbital visual */}
            <motion.div
              className="flex items-center justify-center py-2"
              initial={{ opacity: 0, scale: 0.85 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: 0.3, duration: 0.7, ease: 'easeOut' }}
            >
              <div className="relative flex h-44 w-44 items-center justify-center">
                {/* Outer orbit */}
                <div className="absolute h-44 w-44 rounded-full border border-primary/10 animate-[spin_22s_linear_infinite]">
                  <div className="absolute -top-1 left-1/2 -translate-x-1/2 h-2 w-2 rounded-full bg-primary/50" style={{ boxShadow: '0 0 8px hsl(252 90% 67% / 0.8)' }} />
                </div>
                {/* Middle orbit */}
                <div className="absolute h-32 w-32 rounded-full border border-primary/18" style={{ animation: 'spin 14s linear infinite reverse' }}>
                  <div className="absolute -top-1 left-1/2 -translate-x-1/2 h-1.5 w-1.5 rounded-full bg-sky-400/70" style={{ boxShadow: '0 0 6px hsl(200 95% 60% / 0.8)' }} />
                </div>
                {/* Pulse rings */}
                <div className="absolute h-20 w-20 rounded-full bg-primary/5 animate-ping" style={{ animationDuration: '3s' }} />
                <div className="absolute h-20 w-20 rounded-full bg-primary/5 animate-ping" style={{ animationDuration: '3s', animationDelay: '1.2s' }} />
                {/* Center orb */}
                <div className="relative z-10 flex h-[52px] w-[52px] items-center justify-center rounded-full bg-primary/12 border border-primary/30"
                     style={{ boxShadow: '0 0 24px hsl(252 90% 67% / 0.45), inset 0 1px 0 hsl(0 0% 100% / 0.12)' }}>
                  <PhoneCall className="h-6 w-6 text-primary" />
                </div>
              </div>
            </motion.div>

            {/* Feature chips */}
            <motion.div
              className="flex flex-wrap gap-2"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.5, duration: 0.5 }}
            >
              {FEATURES.map(({ icon: Icon, text }, i) => (
                <motion.div
                  key={text}
                  className="flex items-center gap-2 rounded-full border border-border/50 bg-card/40 backdrop-blur-sm px-3 py-1.5 text-xs text-muted-foreground"
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.55 + i * 0.07, duration: 0.3, ease: 'easeOut' }}
                >
                  <Icon className="h-3 w-3 text-primary shrink-0" />
                  {text}
                </motion.div>
              ))}
            </motion.div>
          </div>

          {/* Stats + footer */}
          <div className="relative z-10 space-y-4">
            <motion.div
              className="grid grid-cols-3 gap-3"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.8, duration: 0.4 }}
            >
              {STATS.map(({ value, label }) => (
                <div key={label} className="rounded-xl border border-border/30 bg-card/30 backdrop-blur-sm px-3 py-3 text-center"
                     style={{ boxShadow: 'inset 0 1px 0 hsl(0 0% 100% / 0.06)' }}>
                  <p className="text-lg font-bold gradient-text">{value}</p>
                  <p className="mt-0.5 text-[10px] text-muted-foreground uppercase tracking-wider">{label}</p>
                </div>
              ))}
            </motion.div>
            <p className="text-xs text-muted-foreground/60">© 2026 VoxAI · All rights reserved</p>
          </div>
        </div>

        {/* ── Right: form ── */}
        <div
          className="relative flex items-center justify-center px-6 py-10 lg:p-12 overflow-hidden"
          style={{ perspective: '900px' }}
          onMouseMove={onFormMove}
          onMouseLeave={onFormLeave}
        >
          {/* Background bloom */}
          <div className="absolute top-0 right-0 h-96 w-96 rounded-full bg-primary/6 blur-3xl pointer-events-none" />
          <div className="absolute bottom-0 left-0 h-64 w-64 rounded-full bg-sky-500/4 blur-3xl pointer-events-none" />

          <motion.div
            className="relative w-full max-w-sm"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: 'easeOut' }}
            style={{ rotateX: tiltX, rotateY: tiltY }}
          >
            <div className="sm:hidden mb-6">
              <Logo />
            </div>

            {/* Form card */}
            <div className="rounded-2xl border border-border/50 bg-card/60 backdrop-blur-xl p-8 space-y-6"
                 style={{ boxShadow: 'inset 0 1px 0 hsl(0 0% 100% / 0.06), 0 20px 60px hsl(0 0% 0% / 0.30)' }}>
              <div>
                <h2 className="text-2xl font-bold tracking-tight">Welcome back</h2>
                <p className="mt-1 text-sm text-muted-foreground">Sign in to your AI dashboard.</p>
              </div>

              <form onSubmit={form.handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="email">Email</Label>
                  <Input id="email" type="email" autoComplete="email"
                    placeholder="you@business.com" {...form.register('email')} />
                  {form.formState.errors.email && (
                    <p className="text-xs text-destructive">{form.formState.errors.email.message}</p>
                  )}
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="password">Password</Label>
                  <div className="relative">
                    <Input id="password" type={showPwd ? 'text' : 'password'}
                      autoComplete="current-password" placeholder="••••••••"
                      className="pr-10" {...form.register('password')} />
                    <button type="button" onClick={() => setShowPwd((s) => !s)}
                      aria-label={showPwd ? 'Hide password' : 'Show password'}
                      className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground transition-colors">
                      {showPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {form.formState.errors.password && (
                    <p className="text-xs text-destructive">{form.formState.errors.password.message}</p>
                  )}
                </div>

                {error && (
                  <motion.div initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }}
                    className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                    {error}
                  </motion.div>
                )}

                <Button type="submit" loading={mutation.isPending} className="w-full gap-2 mt-2">
                  Sign in <ArrowRight className="h-4 w-4" />
                </Button>
              </form>

              <p className="text-center text-sm text-muted-foreground">
                New here?{' '}
                <Link to="/register" className="font-semibold text-primary hover:underline">
                  Create an account
                </Link>
              </p>
            </div>
          </motion.div>
        </div>

      </div>
    </div>
  );
}
