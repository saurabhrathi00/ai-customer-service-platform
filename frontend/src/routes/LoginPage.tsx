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
  { icon: Brain, text: 'Multilingual STT and natural-sounding voice' },
  { icon: Star, text: 'Auto-summaries and interest scoring' },
  { icon: PhoneCall, text: 'Callback queue you can work in minutes' },
  { icon: Shield, text: 'Strict per-business data isolation' },
];

const STATS = [
  { value: '24/7', label: 'Availability' },
  { value: '< 1s', label: 'Response time' },
  { value: '99.9%', label: 'Uptime' },
];

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string })?.from ?? '/';
  const setTokens = useAuthStore((s) => s.setTokens);

  if (isAuthenticated()) {
    return <Navigate to={from} replace />;
  }

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
  // dot: tight spring — closely tracks the pointer
  const dotX = useSpring(cursorX, { stiffness: 700, damping: 40, mass: 0.4 });
  const dotY = useSpring(cursorY, { stiffness: 700, damping: 40, mass: 0.4 });
  // ring: medium spring — lags a little
  const ringX = useSpring(cursorX, { stiffness: 220, damping: 30, mass: 0.7 });
  const ringY = useSpring(cursorY, { stiffness: 220, damping: 30, mass: 0.7 });
  // glow blob: slow spring — trails noticeably
  const glowX = useSpring(cursorX, { stiffness: 70, damping: 22, mass: 1 });
  const glowY = useSpring(cursorY, { stiffness: 70, damping: 22, mass: 1 });

  React.useEffect(() => {
    const onMove = (e: MouseEvent) => {
      cursorX.set(e.clientX);
      cursorY.set(e.clientY);
    };
    window.addEventListener('mousemove', onMove);
    return () => window.removeEventListener('mousemove', onMove);
  }, [cursorX, cursorY]);

  // ── Left panel: spotlight + parallax orbs ─────────────────────
  const panelX = useMotionValue(300);
  const panelY = useMotionValue(400);
  const panelSpringX = useSpring(panelX, { stiffness: 130, damping: 28 });
  const panelSpringY = useSpring(panelY, { stiffness: 130, damping: 28 });
  const spotlight = useMotionTemplate`radial-gradient(520px circle at ${panelSpringX}px ${panelSpringY}px, hsl(262 80% 68% / 0.20), transparent 62%)`;

  const orb1X = useTransform(panelSpringX, [0, 640], [18, -18]);
  const orb1Y = useTransform(panelSpringY, [0, 900], [18, -18]);
  const orb2X = useTransform(panelSpringX, [0, 640], [-22, 22]);
  const orb2Y = useTransform(panelSpringY, [0, 900], [-14, 14]);

  function onPanelMove(e: React.MouseEvent<HTMLDivElement>) {
    const r = e.currentTarget.getBoundingClientRect();
    panelX.set(e.clientX - r.left);
    panelY.set(e.clientY - r.top);
  }

  // ── Right panel: 3-D form tilt ────────────────────────────────
  const formNormX = useMotionValue(0.5);
  const formNormY = useMotionValue(0.5);
  const tiltX = useSpring(useTransform(formNormY, [0, 1], [3, -3]), { stiffness: 130, damping: 28 });
  const tiltY = useSpring(useTransform(formNormX, [0, 1], [-3, 3]), { stiffness: 130, damping: 28 });

  function onFormMove(e: React.MouseEvent<HTMLDivElement>) {
    const r = e.currentTarget.getBoundingClientRect();
    formNormX.set((e.clientX - r.left) / r.width);
    formNormY.set((e.clientY - r.top) / r.height);
  }
  function onFormLeave() {
    formNormX.set(0.5);
    formNormY.set(0.5);
  }

  return (
    <div className="min-h-screen w-full bg-background overflow-hidden lg:cursor-none">

      {/* ── Cursor trail ── */}
      <CursorTrail />

      {/* ── Custom cursor (desktop only) ── */}
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

      <div className="grid min-h-screen lg:grid-cols-2">

        {/* ── Left: marketing column ── */}
        <div
          className="relative hidden lg:flex flex-col justify-between p-12 border-r border-border/40 overflow-hidden"
          onMouseMove={onPanelMove}
        >
          {/* Static backgrounds */}
          <div className="absolute inset-0 aurora-bg" />
          <div className="absolute inset-0 dot-grid opacity-60" />

          {/* Cursor spotlight overlay */}
          <motion.div
            className="pointer-events-none absolute inset-0 z-[5]"
            style={{ background: spotlight }}
          />

          {/* Parallax orbs */}
          <motion.div
            className="absolute top-1/4 left-1/4 h-64 w-64 rounded-full bg-primary/10 blur-3xl"
            style={{ x: orb1X, y: orb1Y }}
          />
          <motion.div
            className="absolute bottom-1/4 right-1/4 h-48 w-48 rounded-full bg-blue-500/8 blur-3xl"
            style={{ x: orb2X, y: orb2Y }}
          />

          <div className="relative z-10">
            <Logo />
          </div>

          <div className="relative z-10 max-w-md">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, ease: 'easeOut' }}
            >
              <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-primary/30 bg-primary/10 px-3 py-1.5 text-xs font-medium text-primary">
                <Sparkles className="h-3 w-3" />
                Powered by Gemini AI
              </div>
              <h1 className="text-4xl font-bold leading-tight tracking-tight">
                Your AI voice agent,
                <br />
                <span className="gradient-text">handling every call.</span>
              </h1>
              <p className="mt-4 text-muted-foreground leading-relaxed">
                VoxAI answers your business calls 24/7 using the knowledge you
                configure, summarises every conversation, and surfaces the leads
                worth calling back.
              </p>
            </motion.div>

            <motion.ul
              className="mt-8 space-y-3"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.3, duration: 0.5 }}
            >
              {FEATURES.map(({ icon: Icon, text }, i) => (
                <motion.li
                  key={text}
                  className="flex items-center gap-3 text-sm"
                  initial={{ opacity: 0, x: -12 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.4 + i * 0.08, duration: 0.35 }}
                >
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-primary/15 text-primary">
                    <Icon className="h-3.5 w-3.5" />
                  </div>
                  <span className="text-muted-foreground">{text}</span>
                </motion.li>
              ))}
            </motion.ul>

            <motion.div
              className="mt-10 grid grid-cols-3 gap-4"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.7, duration: 0.4 }}
            >
              {STATS.map(({ value, label }) => (
                <div key={label} className="rounded-xl border border-border/40 bg-card/40 backdrop-blur-sm px-4 py-3 text-center">
                  <p className="text-xl font-bold gradient-text">{value}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">{label}</p>
                </div>
              ))}
            </motion.div>
          </div>

          <div className="relative z-10">
            <p className="text-xs text-muted-foreground">© 2026 VoxAI · All rights reserved</p>
          </div>
        </div>

        {/* ── Right: form column ── */}
        <div
          className="relative flex items-center justify-center px-6 py-10 lg:p-12 overflow-hidden"
          style={{ perspective: '900px' }}
          onMouseMove={onFormMove}
          onMouseLeave={onFormLeave}
        >
          <div className="absolute top-0 right-0 h-96 w-96 rounded-full bg-primary/5 blur-3xl pointer-events-none" />

          <motion.form
            onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
            className="relative w-full max-w-sm space-y-6"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: 'easeOut' }}
            style={{ rotateX: tiltX, rotateY: tiltY }}
          >
            <div className="lg:hidden mb-2">
              <Logo />
            </div>

            <div>
              <h2 className="text-2xl font-bold tracking-tight">Welcome back</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Sign in to access your AI dashboard.
              </p>
            </div>

            <div className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="you@business.com"
                  {...form.register('email')}
                />
                {form.formState.errors.email && (
                  <p className="text-xs text-destructive">{form.formState.errors.email.message}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="password">Password</Label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPwd ? 'text' : 'password'}
                    autoComplete="current-password"
                    placeholder="••••••••"
                    className="pr-10"
                    {...form.register('password')}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPwd((s) => !s)}
                    aria-label={showPwd ? 'Hide password' : 'Show password'}
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground transition-colors"
                  >
                    {showPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {form.formState.errors.password && (
                  <p className="text-xs text-destructive">
                    {form.formState.errors.password.message}
                  </p>
                )}
              </div>
            </div>

            {error && (
              <motion.div
                initial={{ opacity: 0, scale: 0.97 }}
                animate={{ opacity: 1, scale: 1 }}
                className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
              >
                {error}
              </motion.div>
            )}

            <Button type="submit" loading={mutation.isPending} className="w-full gap-2">
              Sign in <ArrowRight className="h-4 w-4" />
            </Button>

            <p className="text-center text-sm text-muted-foreground">
              New here?{' '}
              <Link to="/register" className="font-semibold text-primary hover:underline">
                Create an account
              </Link>
            </p>
          </motion.form>
        </div>

      </div>
    </div>
  );
}
