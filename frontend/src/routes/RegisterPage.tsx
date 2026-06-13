import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Brain, PhoneCall, Star, Shield, Sparkles, ArrowRight, Eye, EyeOff } from 'lucide-react';
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
import { auth, business } from '@/api/resources';
import { useAuthStore } from '@/store/auth';

const schema = z.object({
  name: z.string().min(2, 'Business name is required').max(200),
  email: z.string().email('Enter a valid email'),
  password: z.string().min(8, 'Use at least 8 characters').max(100),
  category: z.string().optional(),
  location: z.string().optional(),
  whatsappNumber: z
    .string()
    .regex(/^\+[1-9]\d{6,14}$/, 'Use E.164 format e.g. +919900112233')
    .optional()
    .or(z.literal('')),
});

type Values = z.infer<typeof schema>;

const FEATURES = [
  { icon: Brain,     text: 'AI agent live in under 5 minutes' },
  { icon: PhoneCall, text: 'Answers calls 24/7, never misses one' },
  { icon: Star,      text: 'Auto-scores leads, requests callbacks' },
  { icon: Shield,    text: 'Your data stays isolated, always' },
];

const STEPS = [
  { step: '1', label: 'Create account' },
  { step: '2', label: 'Add knowledge' },
  { step: '3', label: 'Go live' },
];

export default function RegisterPage() {
  const navigate = useNavigate();
  const setTokens = useAuthStore((s) => s.setTokens);
  const [showPwd, setShowPwd] = React.useState(false);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '', email: '', password: '', category: '', location: '', whatsappNumber: '',
    },
  });
  const [error, setError] = React.useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: async (v: Values) => {
      const payload = { ...v, whatsappNumber: v.whatsappNumber?.trim() || undefined };
      await business.register(payload);
      return auth.signin(v.email, v.password);
    },
    onSuccess: (data) => {
      setError(null);
      setTokens(data.token, data.refreshToken);
      toast.success('Account created. Welcome!');
      navigate('/', { replace: true });
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: { message?: string; error?: string } }; message?: string };
      const msg =
        e?.response?.data?.message ??
        e?.response?.data?.error ??
        e?.message ??
        'Registration failed. Please try again.';
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
  const panelY = useMotionValue(500);
  const panelSpringX = useSpring(panelX, { stiffness: 130, damping: 28 });
  const panelSpringY = useSpring(panelY, { stiffness: 130, damping: 28 });
  const spotlight = useMotionTemplate`radial-gradient(520px circle at ${panelSpringX}px ${panelSpringY}px, hsl(252 90% 67% / 0.18), transparent 62%)`;

  const orb1X = useTransform(panelSpringX, [0, 640], [18, -18]);
  const orb1Y = useTransform(panelSpringY, [0, 900], [18, -18]);
  const orb2X = useTransform(panelSpringX, [0, 640], [-22, 22]);
  const orb2Y = useTransform(panelSpringY, [0, 900], [-14, 14]);

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
    <div className="min-h-screen w-full bg-background overflow-x-hidden lg:cursor-none">

      {/* ── Top nav bar ── */}
      <header className="fixed top-0 inset-x-0 z-[190] flex items-center justify-between px-6 py-3 border-b border-border/30 bg-background/70 backdrop-blur-md">
        <Logo />
        <nav className="hidden sm:flex items-center gap-1">
          {[
            { label: 'About',   to: '/about' },
            { label: 'Pricing', to: '/pricing' },
            { label: 'Support', to: '/support' },
          ].map(({ label, to }) => (
            <Link
              key={to}
              to={to}
              className="px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground rounded-md hover:bg-accent/40 transition-colors"
            >
              {label}
            </Link>
          ))}
        </nav>
        <Link to="/login">
          <Button size="sm" variant="outline" className="gap-1.5">Sign in</Button>
        </Link>
      </header>

      <CursorTrail />

      {/* ── Custom cursor (desktop only) ── */}
      <motion.div
        className="pointer-events-none fixed z-[200] hidden lg:block h-2.5 w-2.5 rounded-full bg-primary"
        style={{ left: dotX, top: dotY, translateX: '-50%', translateY: '-50%',
          boxShadow: '0 0 10px hsl(252 90% 67% / 0.8)' }}
      />
      <motion.div
        className="pointer-events-none fixed z-[199] hidden lg:block h-7 w-7 rounded-full border border-primary/60"
        style={{ left: ringX, top: ringY, translateX: '-50%', translateY: '-50%' }}
      />
      <motion.div
        className="pointer-events-none fixed z-[198] hidden lg:block h-28 w-28 rounded-full bg-primary/8 blur-2xl"
        style={{ left: glowX, top: glowY, translateX: '-50%', translateY: '-50%' }}
      />

      <div className="grid min-h-screen lg:grid-cols-2 pt-[57px]">

        {/* ── Left: marketing column ── */}
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

          {/* Logo */}
          <div className="relative z-10">
            <Logo />
          </div>

          {/* Hero content */}
          <div className="relative z-10 space-y-8">
            <motion.div
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, ease: 'easeOut' }}
            >
              <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-primary/25 bg-primary/8 px-3 py-1.5 text-xs font-semibold text-primary">
                <Sparkles className="h-3 w-3" />
                Set up in under 5 minutes
              </div>
              <h1 className="text-[2.6rem] font-bold leading-[1.15] tracking-[-0.03em]">
                Deploy your AI agent
                <br />
                <span className="gradient-text">before lunch.</span>
              </h1>
              <p className="mt-4 text-[0.95rem] text-muted-foreground leading-relaxed max-w-sm">
                No code, no hardware, no hiring. Just add your business knowledge
                and your AI voice agent starts answering calls immediately.
              </p>
            </motion.div>

            {/* AI orbital visualization */}
            <motion.div
              className="flex items-center justify-center py-2"
              initial={{ opacity: 0, scale: 0.85 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: 0.3, duration: 0.7, ease: 'easeOut' }}
            >
              <div className="relative flex h-44 w-44 items-center justify-center">
                {/* Outer orbit */}
                <div className="absolute h-44 w-44 rounded-full border border-primary/10 animate-[spin_22s_linear_infinite]">
                  <div
                    className="absolute -top-1 left-1/2 -translate-x-1/2 h-2 w-2 rounded-full bg-primary/50"
                    style={{ boxShadow: '0 0 8px hsl(252 90% 67% / 0.8)' }}
                  />
                </div>
                {/* Middle orbit */}
                <div
                  className="absolute h-32 w-32 rounded-full border border-primary/18"
                  style={{ animation: 'spin 14s linear infinite reverse' }}
                >
                  <div
                    className="absolute -top-1 left-1/2 -translate-x-1/2 h-1.5 w-1.5 rounded-full bg-sky-400/70"
                    style={{ boxShadow: '0 0 6px hsl(200 95% 60% / 0.8)' }}
                  />
                </div>
                {/* Pulse rings */}
                <div className="absolute h-20 w-20 rounded-full bg-primary/5 animate-ping" style={{ animationDuration: '3s' }} />
                <div className="absolute h-20 w-20 rounded-full bg-primary/5 animate-ping" style={{ animationDuration: '3s', animationDelay: '1.2s' }} />
                {/* Center orb */}
                <div
                  className="relative z-10 flex h-[52px] w-[52px] items-center justify-center rounded-full bg-primary/12 border border-primary/30"
                  style={{ boxShadow: '0 0 24px hsl(252 90% 67% / 0.45), inset 0 1px 0 hsl(0 0% 100% / 0.12)' }}
                >
                  <Brain className="h-6 w-6 text-primary" />
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

            {/* How it works stepper */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.7, duration: 0.5 }}
            >
              <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-4">How it works</p>
              <div className="flex items-center gap-3">
                {STEPS.map(({ step, label }, i) => (
                  <React.Fragment key={step}>
                    <motion.div
                      className="flex flex-col items-center gap-1.5"
                      initial={{ opacity: 0, y: 8 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.75 + i * 0.1, duration: 0.3 }}
                    >
                      <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/20 text-primary text-sm font-bold">
                        {step}
                      </div>
                      <span className="text-xs text-muted-foreground whitespace-nowrap">{label}</span>
                    </motion.div>
                    {i < STEPS.length - 1 && (
                      <div className="flex-1 h-px bg-gradient-to-r from-primary/30 to-primary/10 mb-5" />
                    )}
                  </React.Fragment>
                ))}
              </div>
            </motion.div>
          </div>

          {/* Footer */}
          <div className="relative z-10">
            <p className="text-xs text-muted-foreground/60">© 2026 VoxAI · All rights reserved</p>
          </div>
        </div>

        {/* ── Right: form column ── */}
        <div
          className="relative flex items-center justify-center px-6 py-10 lg:p-12 overflow-hidden"
          style={{ perspective: '900px' }}
          onMouseMove={onFormMove}
          onMouseLeave={onFormLeave}
        >
          {/* Background blooms */}
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
            <div
              className="rounded-2xl border border-border/50 bg-card/60 backdrop-blur-xl p-5 sm:p-8 space-y-6"
              style={{ boxShadow: 'inset 0 1px 0 hsl(0 0% 100% / 0.06), 0 20px 60px hsl(0 0% 0% / 0.30)' }}
            >
              <div>
                <h2 className="text-2xl font-bold tracking-tight">Create your account</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Get your AI voice agent running in minutes.
                </p>
              </div>

              <form
                onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
                className="space-y-3"
              >
                <Field label="Business name" error={form.formState.errors.name?.message}>
                  <Input placeholder="Acme Dental" {...form.register('name')} />
                </Field>
                <Field label="Email" error={form.formState.errors.email?.message}>
                  <Input type="email" autoComplete="email" placeholder="owner@business.com" {...form.register('email')} />
                </Field>
                <Field label="Password" error={form.formState.errors.password?.message}>
                  <div className="relative">
                    <Input
                      type={showPwd ? 'text' : 'password'}
                      autoComplete="new-password"
                      placeholder="At least 8 characters"
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
                </Field>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <Field label="Category" hint="optional">
                    <Input placeholder="Dental clinic" {...form.register('category')} />
                  </Field>
                  <Field label="Location" hint="optional">
                    <Input placeholder="Mumbai, IN" {...form.register('location')} />
                  </Field>
                </div>

                <Field
                  label="WhatsApp number"
                  hint="optional, add later"
                  error={form.formState.errors.whatsappNumber?.message}
                >
                  <Input
                    placeholder="+919900112233"
                    className="font-mono"
                    {...form.register('whatsappNumber')}
                  />
                </Field>

                {error && (
                  <motion.div
                    initial={{ opacity: 0, scale: 0.97 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
                  >
                    {error}
                  </motion.div>
                )}

                <Button type="submit" loading={mutation.isPending} className="w-full gap-2 mt-2">
                  Create account <ArrowRight className="h-4 w-4" />
                </Button>
              </form>

              <p className="text-center text-sm text-muted-foreground">
                Already have an account?{' '}
                <Link to="/login" className="font-semibold text-primary hover:underline">
                  Sign in
                </Link>
              </p>
            </div>
          </motion.div>
        </div>

      </div>
    </div>
  );
}

function Field({
  label,
  children,
  error,
  hint,
}: {
  label: string;
  children: React.ReactNode;
  error?: string;
  hint?: string;
}) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between">
        <Label>{label}</Label>
        {hint && <span className="text-xs text-muted-foreground">{hint}</span>}
      </div>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}
