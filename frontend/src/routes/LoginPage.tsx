import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Eye, EyeOff, ArrowRight } from 'lucide-react';
import * as React from 'react';

import { Logo } from '@/components/app/Logo';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { auth } from '@/api/resources';
import { useAuthStore } from '@/store/auth';

const schema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
});

type Values = z.infer<typeof schema>;

export default function LoginPage() {
  const navigate = useNavigate();
  const setTokens = useAuthStore((s) => s.setTokens);
  const [showPwd, setShowPwd] = React.useState(false);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  });

  const mutation = useMutation({
    mutationFn: (v: Values) => auth.signin(v.email, v.password),
    onSuccess: (data) => {
      setTokens(data.token, data.refreshToken);
      toast.success('Welcome back');
      navigate('/', { replace: true });
    },
  });

  return (
    <div className="min-h-screen w-full mesh-bg">
      <div className="grid min-h-screen lg:grid-cols-2">
        {/* Marketing column */}
        <div className="relative hidden lg:flex flex-col justify-between p-12 text-foreground border-r">
          <Logo />
          <div className="max-w-md">
            <h1 className="text-4xl font-semibold leading-tight tracking-tight">
              Your AI receptionist,
              <br />
              answering every call.
            </h1>
            <p className="mt-4 text-muted-foreground">
              VoxAI answers your business calls 24/7 using the knowledge you
              configure, summarises every conversation, and surfaces the leads
              worth calling back.
            </p>
            <ul className="mt-8 space-y-3 text-sm">
              {[
                'Multilingual STT and natural-sounding voice',
                'Auto-summaries and interest scoring',
                'Callback queue you can work in minutes',
                'Strict per-business data isolation',
              ].map((f) => (
                <li key={f} className="flex items-start gap-2">
                  <span className="mt-1 h-1.5 w-1.5 rounded-full bg-primary" />
                  <span className="text-muted-foreground">{f}</span>
                </li>
              ))}
            </ul>
          </div>
          <p className="text-xs text-muted-foreground">© VoxAI · All rights reserved</p>
        </div>

        {/* Form column */}
        <div className="flex items-center justify-center px-6 py-10 lg:p-12">
          <form
            onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
            className="w-full max-w-sm space-y-6"
          >
            <div className="lg:hidden">
              <Logo />
            </div>
            <div>
              <h2 className="text-2xl font-semibold tracking-tight">Sign in</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Welcome back. Enter your details to access the dashboard.
              </p>
            </div>

            <div className="space-y-3">
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
                <div className="flex items-center justify-between">
                  <Label htmlFor="password">Password</Label>
                </div>
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
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground"
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

            <Button type="submit" loading={mutation.isPending} className="w-full">
              Sign in <ArrowRight className="h-4 w-4" />
            </Button>

            <p className="text-center text-sm text-muted-foreground">
              New here?{' '}
              <Link to="/register" className="font-medium text-primary hover:underline">
                Create an account
              </Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}
