import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { Phone, Plus, Trash2 } from 'lucide-react';

import { business } from '@/api/resources';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Dialog, DialogBody, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { useAuthStore } from '@/store/auth';

function normalizePhone(raw: string, type: 'mobile' | 'landline'): string {
  const digits = raw.replace(/[\s\-()]/g, '');
  if (digits.startsWith('+')) return digits;
  if (type === 'landline') {
    if (digits.startsWith('0')) return '+91' + digits.substring(1);
    return '+91' + digits;
  }
  if (digits.startsWith('0')) return '+91' + digits.substring(1);
  if (/^\d{10}$/.test(digits)) return '+91' + digits;
  return digits;
}

const mobileSchema = z.object({
  phoneType: z.literal('mobile'),
  twilioNumber: z
    .string()
    .min(1, 'Phone number is required')
    .refine((v) => {
      const digits = v.replace(/[\s\-()]/g, '');
      if (digits.startsWith('+91')) return /^\+91\d{10}$/.test(digits);
      if (digits.startsWith('0')) return /^0\d{10}$/.test(digits);
      return /^\d{10}$/.test(digits);
    }, 'Enter a valid 10-digit mobile number'),
  label: z.string().max(100).optional(),
});

const landlineSchema = z.object({
  phoneType: z.literal('landline'),
  twilioNumber: z
    .string()
    .min(1, 'Phone number is required')
    .refine((v) => {
      const digits = v.replace(/[\s\-()]/g, '');
      if (digits.startsWith('+91')) return /^\+91\d{7,11}$/.test(digits);
      if (digits.startsWith('0')) return /^0\d{7,11}$/.test(digits);
      return /^\d{7,11}$/.test(digits);
    }, 'Enter a valid landline number (with STD code)'),
  label: z.string().max(100).optional(),
});

const schema = z.discriminatedUnion('phoneType', [mobileSchema, landlineSchema]);
type Values = z.infer<typeof schema>;

export function PhoneNumbersCard() {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const numbers = useQuery({
    queryKey: ['business', 'phone-numbers', businessId],
    queryFn: () => business.phoneNumbers(businessId),
  });

  const remove = useMutation({
    mutationFn: (numberId: string) => business.removePhoneNumber(businessId, numberId),
    onSuccess: () => {
      toast.success('Number removed');
      qc.invalidateQueries({ queryKey: ['business', 'phone-numbers', businessId] });
    },
  });

  return (
    <>
      <Card>
        <CardHeader>
          <div className="flex items-start justify-between gap-4">
            <div>
              <CardTitle>Phone numbers</CardTitle>
              <CardDescription>
                The Twilio numbers that route to your AI receptionist.
              </CardDescription>
            </div>
            <Button onClick={() => setOpen(true)}>
              <Plus className="h-4 w-4" /> Add number
            </Button>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {numbers.isLoading ? (
            <div className="p-4 space-y-3">
              {Array.from({ length: 2 }).map((_, i) => (
                <Skeleton key={i} className="h-14" />
              ))}
            </div>
          ) : (numbers.data?.length ?? 0) === 0 ? (
            <EmptyState
              icon={<Phone className="h-5 w-5" />}
              title="No numbers yet"
              description="Add the Twilio number you've configured to forward calls to VoxAI."
              action={
                <Button onClick={() => setOpen(true)}>
                  <Plus className="h-4 w-4" /> Add number
                </Button>
              }
              className="py-10"
            />
          ) : (
            <ul className="divide-y">
              {numbers.data!.map((n) => (
                <li key={n.id} className="group flex items-center justify-between p-4 transition-colors hover:bg-accent/30">
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="grid h-9 w-9 place-items-center rounded-md bg-primary/10 text-primary">
                      <Phone className="h-4 w-4" />
                    </div>
                    <div className="min-w-0">
                      <p className="font-mono text-sm font-medium">{n.twilioNumber}</p>
                      <p className="text-xs text-muted-foreground truncate">
                        {n.label ?? 'Unlabeled'}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    {n.isActive ? (
                      <Badge variant="success">Active</Badge>
                    ) : (
                      <Badge variant="secondary">Inactive</Badge>
                    )}
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => {
                        if (confirm(`Remove ${n.twilioNumber}?`)) remove.mutate(n.id);
                      }}
                      className="opacity-0 transition-opacity group-hover:opacity-100"
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <AddDialog open={open} onOpenChange={setOpen} />
    </>
  );
}

function AddDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { phoneType: 'mobile', twilioNumber: '', label: '' },
  });

  const phoneType = form.watch('phoneType');

  const add = useMutation({
    mutationFn: (v: Values) =>
      business.addPhoneNumber(businessId, {
        twilioNumber: normalizePhone(v.twilioNumber, v.phoneType),
        label: v.label,
      }),
    onSuccess: () => {
      toast.success('Number added');
      qc.invalidateQueries({ queryKey: ['business', 'phone-numbers', businessId] });
      onOpenChange(false);
      form.reset();
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogHeader>
        <DialogTitle>Add phone number</DialogTitle>
      </DialogHeader>
      <form
        id="add-phone-form"
        onSubmit={form.handleSubmit((v) => add.mutate(v))}
      >
        <DialogBody className="space-y-4">
          <div className="space-y-1.5">
            <Label>Type</Label>
            <div className="flex gap-2">
              <button
                type="button"
                className={`flex-1 rounded-md border px-3 py-2 text-sm font-medium transition-colors ${
                  phoneType === 'mobile'
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-border text-muted-foreground hover:bg-muted'
                }`}
                onClick={() => { form.setValue('phoneType', 'mobile'); form.clearErrors('twilioNumber'); }}
              >
                Mobile
              </button>
              <button
                type="button"
                className={`flex-1 rounded-md border px-3 py-2 text-sm font-medium transition-colors ${
                  phoneType === 'landline'
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-border text-muted-foreground hover:bg-muted'
                }`}
                onClick={() => { form.setValue('phoneType', 'landline'); form.clearErrors('twilioNumber'); }}
              >
                Landline
              </button>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Phone number</Label>
            <Input
              placeholder={phoneType === 'mobile' ? '9876543210' : '01141186129'}
              className="font-mono"
              inputMode="tel"
              {...form.register('twilioNumber')}
            />
            <p className="text-xs text-muted-foreground">
              {phoneType === 'mobile' ? '10-digit mobile number' : 'Landline with STD code (e.g. 01141186129)'}
            </p>
            {form.formState.errors.twilioNumber && (
              <p className="text-xs text-destructive">
                {form.formState.errors.twilioNumber.message}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label>Label (optional)</Label>
            <Input placeholder="Main reception" {...form.register('label')} />
          </div>
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="add-phone-form" loading={add.isPending}>
            Add number
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
