import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ArrowRight } from 'lucide-react';

import { Logo } from '@/components/app/Logo';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
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

export default function RegisterPage() {
  const navigate = useNavigate();
  const setTokens = useAuthStore((s) => s.setTokens);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '', email: '', password: '', category: '', location: '', whatsappNumber: '',
    },
  });

  const mutation = useMutation({
    mutationFn: async (v: Values) => {
      // Backend rejects empty-string whatsapp; send undefined when blank.
      const payload = { ...v, whatsappNumber: v.whatsappNumber?.trim() || undefined };
      await business.register(payload);
      return auth.signin(v.email, v.password);
    },
    onSuccess: (data) => {
      setTokens(data.token, data.refreshToken);
      toast.success('Account created. Welcome!');
      navigate('/', { replace: true });
    },
  });

  return (
    <div className="min-h-screen w-full mesh-bg flex items-center justify-center p-4 sm:p-8">
      <div className="w-full max-w-md rounded-2xl border bg-card p-8 shadow-xl animate-slide-up">
        <Logo />
        <h2 className="mt-6 text-2xl font-semibold tracking-tight">Create your account</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Get your AI receptionist set up in under five minutes.
        </p>

        <form
          onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
          className="mt-6 space-y-3"
        >
          <Field label="Business name" error={form.formState.errors.name?.message}>
            <Input placeholder="Acme Dental" {...form.register('name')} />
          </Field>
          <Field label="Email" error={form.formState.errors.email?.message}>
            <Input type="email" autoComplete="email" placeholder="owner@business.com" {...form.register('email')} />
          </Field>
          <Field label="Password" error={form.formState.errors.password?.message}>
            <Input
              type="password"
              autoComplete="new-password"
              placeholder="At least 8 characters"
              {...form.register('password')}
            />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Category" hint="optional">
              <Input placeholder="Dental clinic" {...form.register('category')} />
            </Field>
            <Field label="Location" hint="optional">
              <Input placeholder="Mumbai, IN" {...form.register('location')} />
            </Field>
          </div>
          <Field
            label="WhatsApp number"
            hint="for lead notifications (optional, add later)"
            error={form.formState.errors.whatsappNumber?.message}
          >
            <Input
              placeholder="+919900112233"
              className="font-mono"
              {...form.register('whatsappNumber')}
            />
          </Field>

          <Button type="submit" loading={mutation.isPending} className="w-full mt-2">
            Create account <ArrowRight className="h-4 w-4" />
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </p>
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
