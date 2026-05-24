import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Globe, Mail, MapPin, Phone, Sparkles } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';

import { knowledge } from '@/api/resources';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input, Textarea } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { useAuthStore } from '@/store/auth';

const schema = z.object({
  address: z.string().max(1000).optional().or(z.literal('')),
  locationNotes: z.string().max(500).optional().or(z.literal('')),
  altPhone: z
    .string()
    .regex(/^\+?[1-9]\d{6,14}$/, 'Use E.164 (e.g. +919900112233)')
    .optional()
    .or(z.literal('')),
  contactEmail: z.string().email('Enter a valid email').optional().or(z.literal('')),
  websiteUrl: z
    .string()
    .url('Enter a valid URL')
    .startsWith('https://', 'Must use https://')
    .optional()
    .or(z.literal('')),
  appointmentPolicy: z.string().max(2000).optional().or(z.literal('')),
  cancellationPolicy: z.string().max(2000).optional().or(z.literal('')),
  refundPolicy: z.string().max(2000).optional().or(z.literal('')),
});
type Values = z.infer<typeof schema>;

export function ProfileTab() {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();

  const profile = useQuery({
    queryKey: ['knowledge', 'profile', businessId],
    queryFn: () => knowledge.profile(businessId),
  });

  const completeness = useQuery({
    queryKey: ['knowledge', 'completeness', businessId],
    queryFn: () => knowledge.completeness(businessId),
  });

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: {
      address: profile.data?.address ?? '',
      locationNotes: profile.data?.locationNotes ?? '',
      altPhone: profile.data?.altPhone ?? '',
      contactEmail: profile.data?.contactEmail ?? '',
      websiteUrl: profile.data?.websiteUrl ?? '',
      appointmentPolicy: profile.data?.appointmentPolicy ?? '',
      cancellationPolicy: profile.data?.cancellationPolicy ?? '',
      refundPolicy: profile.data?.refundPolicy ?? '',
    },
  });

  const mutation = useMutation({
    mutationFn: (v: Values) =>
      knowledge.upsertProfile(businessId, {
        ...Object.fromEntries(Object.entries(v).map(([k, val]) => [k, val === '' ? null : val])),
      }),
    onSuccess: () => {
      toast.success('Profile updated');
      qc.invalidateQueries({ queryKey: ['knowledge', 'profile', businessId] });
      qc.invalidateQueries({ queryKey: ['knowledge', 'completeness', businessId] });
    },
  });

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="lg:col-span-2 space-y-4">
        <Card>
          <CardHeader>
            <CardTitle>Contact & location</CardTitle>
            <CardDescription>
              The AI agent will share these when callers ask "where are you" or "what's your number".
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form
              id="profile-form"
              onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
              className="space-y-4"
            >
              {profile.isLoading ? (
                <div className="space-y-2">
                  {Array.from({ length: 4 }).map((_, i) => (
                    <Skeleton key={i} className="h-10" />
                  ))}
                </div>
              ) : (
                <>
                  <Field label="Address" icon={<MapPin className="h-4 w-4" />} error={form.formState.errors.address?.message}>
                    <Input placeholder="123 Main Street, City, ZIP" {...form.register('address')} />
                  </Field>
                  <Field
                    label="Location notes"
                    error={form.formState.errors.locationNotes?.message}
                  >
                    <Textarea
                      placeholder="Landmarks, parking, accessibility notes…"
                      rows={2}
                      {...form.register('locationNotes')}
                    />
                  </Field>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field
                      label="Alt. phone"
                      icon={<Phone className="h-4 w-4" />}
                      hint="E.164"
                      error={form.formState.errors.altPhone?.message}
                    >
                      <Input placeholder="+919900112233" {...form.register('altPhone')} />
                    </Field>
                    <Field
                      label="Contact email"
                      icon={<Mail className="h-4 w-4" />}
                      error={form.formState.errors.contactEmail?.message}
                    >
                      <Input
                        type="email"
                        placeholder="hello@business.com"
                        {...form.register('contactEmail')}
                      />
                    </Field>
                  </div>
                  <Field
                    label="Website"
                    icon={<Globe className="h-4 w-4" />}
                    error={form.formState.errors.websiteUrl?.message}
                  >
                    <Input placeholder="https://your-business.com" {...form.register('websiteUrl')} />
                  </Field>
                </>
              )}
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Policies</CardTitle>
            <CardDescription>
              Short prose the AI quotes verbatim when a caller asks.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <Field label="Appointment policy" error={form.formState.errors.appointmentPolicy?.message}>
              <Textarea
                form="profile-form"
                placeholder="How to book, walk-ins, advance booking window…"
                rows={3}
                {...form.register('appointmentPolicy')}
              />
            </Field>
            <Field label="Cancellation policy" error={form.formState.errors.cancellationPolicy?.message}>
              <Textarea
                form="profile-form"
                placeholder="What happens if a customer cancels?"
                rows={3}
                {...form.register('cancellationPolicy')}
              />
            </Field>
            <Field label="Refund policy" error={form.formState.errors.refundPolicy?.message}>
              <Textarea
                form="profile-form"
                placeholder="Refund timelines and eligibility."
                rows={3}
                {...form.register('refundPolicy')}
              />
            </Field>
          </CardContent>
        </Card>

        <div className="flex justify-end">
          <Button
            type="submit"
            form="profile-form"
            loading={mutation.isPending}
            disabled={!form.formState.isDirty}
          >
            Save changes
          </Button>
        </div>
      </div>

      <div className="space-y-4">
        <CompletenessCard score={completeness.data?.score ?? null} missing={completeness.data?.missingFields ?? []} loading={completeness.isLoading} />
      </div>
    </div>
  );
}

function Field({
  label,
  hint,
  icon,
  error,
  children,
}: {
  label: string;
  hint?: string;
  icon?: React.ReactNode;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between">
        <Label className="inline-flex items-center gap-1.5">
          {icon}
          {label}
        </Label>
        {hint && <span className="text-xs text-muted-foreground">{hint}</span>}
      </div>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}

function CompletenessCard({
  score,
  missing,
  loading,
}: {
  score: number | null;
  missing: string[];
  loading: boolean;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-primary" /> Knowledge completeness
        </CardTitle>
        <CardDescription>The more we know, the better the AI sounds.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {loading ? (
          <Skeleton className="h-24" />
        ) : (
          <>
            <div>
              <div className="flex items-baseline justify-between">
                <span className="text-3xl font-semibold tracking-tight">
                  {score ?? 0}
                  <span className="text-base text-muted-foreground">/100</span>
                </span>
                <span className="text-xs text-muted-foreground">
                  {score == null ? 'Not yet calculated' : score >= 80 ? 'Looking great' : 'Keep going'}
                </span>
              </div>
              <div className="mt-3 h-2 w-full rounded-full bg-secondary overflow-hidden">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-primary to-[hsl(214_90%_60%)] transition-all"
                  style={{ width: `${Math.min(100, Math.max(0, score ?? 0))}%` }}
                />
              </div>
            </div>
            {missing.length > 0 && (
              <div className="space-y-2">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Still missing
                </p>
                <ul className="space-y-1 text-sm">
                  {missing.map((m) => (
                    <li key={m} className="flex items-center gap-2">
                      <span className="h-1.5 w-1.5 rounded-full bg-muted-foreground/40" />
                      <span className="capitalize">{m.replace(/[_-]/g, ' ')}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
