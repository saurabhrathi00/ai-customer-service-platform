import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { Building2 } from 'lucide-react';

import { business } from '@/api/resources';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Skeleton } from '@/components/ui/Skeleton';
import { useAuthStore } from '@/store/auth';

const schema = z.object({
  name: z.string().min(2).max(200),
  category: z.string().max(120).optional().or(z.literal('')),
  description: z.string().max(2000).optional().or(z.literal('')),
  location: z.string().max(500).optional().or(z.literal('')),
  operatingHours: z.string().max(500).optional().or(z.literal('')),
  whatsappNumber: z
    .string()
    .regex(/^\+[1-9]\d{6,14}$/, 'Use E.164 format e.g. +919900112233')
    .optional()
    .or(z.literal('')),
});
type Values = z.infer<typeof schema>;

export function BusinessProfileCard() {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();
  const profile = useQuery({
    queryKey: ['business', businessId],
    queryFn: () => business.profile(businessId),
  });

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: {
      name: profile.data?.name ?? '',
      category: profile.data?.category ?? '',
      description: profile.data?.description ?? '',
      location: profile.data?.location ?? '',
      operatingHours: profile.data?.operatingHours ?? '',
      whatsappNumber: profile.data?.whatsappNumber ?? '',
    },
  });

  const save = useMutation({
    mutationFn: (v: Values) =>
      business.updateProfile(businessId, {
        ...v,
        category: v.category || null,
        description: v.description || null,
        location: v.location || null,
        operatingHours: v.operatingHours || null,
        whatsappNumber: v.whatsappNumber || null,
      } as never),
    onSuccess: () => {
      toast.success('Business profile updated');
      qc.invalidateQueries({ queryKey: ['business', businessId] });
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Building2 className="h-4 w-4" /> Business profile
        </CardTitle>
        <CardDescription>The basics of how you describe yourselves.</CardDescription>
      </CardHeader>
      <CardContent>
        {profile.isLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-10" />
            ))}
          </div>
        ) : (
          <form
            onSubmit={form.handleSubmit((v) => save.mutate(v))}
            className="space-y-4"
          >
            <div className="space-y-1.5">
              <Label>Name</Label>
              <Input {...form.register('name')} />
              {form.formState.errors.name && (
                <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
              )}
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Category</Label>
                <Input placeholder="e.g. Dental clinic" {...form.register('category')} />
              </div>
              <div className="space-y-1.5">
                <Label>Location</Label>
                <Input placeholder="City, country" {...form.register('location')} />
              </div>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Operating hours</Label>
                <Input placeholder="Mon–Sat 9am–7pm" {...form.register('operatingHours')} />
              </div>
              <div className="space-y-1.5">
                <Label>WhatsApp number</Label>
                <Input
                  placeholder="+919900112233"
                  className="font-mono"
                  {...form.register('whatsappNumber')}
                />
                {form.formState.errors.whatsappNumber && (
                  <p className="text-xs text-destructive">
                    {form.formState.errors.whatsappNumber.message}
                  </p>
                )}
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>Description</Label>
              <Textarea
                rows={3}
                placeholder="One paragraph the AI can quote when callers ask 'what do you do?'"
                {...form.register('description')}
              />
            </div>
            <div className="flex justify-end">
              <Button
                type="submit"
                disabled={!form.formState.isDirty}
                loading={save.isPending}
              >
                Save changes
              </Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
