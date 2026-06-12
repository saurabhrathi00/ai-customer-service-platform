import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { MessageSquare, Plus, Trash2 } from 'lucide-react';

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

const schema = z.object({
  whatsappNumber: z
    .string()
    .min(1, 'WhatsApp number is required')
    .regex(/^\+[1-9]\d{6,14}$/, 'Use E.164 format, e.g. +9198XXXXXXXX'),
  label: z.string().max(100).optional(),
});
type Values = z.infer<typeof schema>;

export function NotificationRecipientsCard() {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const profile = useQuery({
    queryKey: ['business', 'profile', businessId],
    queryFn: () => business.profile(businessId),
  });

  const recipients = useQuery({
    queryKey: ['business', 'notification-recipients', businessId],
    queryFn: () => business.notificationRecipients(businessId),
  });

  const remove = useMutation({
    mutationFn: (recipientId: string) =>
      business.removeNotificationRecipient(businessId, recipientId),
    onSuccess: () => {
      toast.success('Recipient removed');
      qc.invalidateQueries({ queryKey: ['business', 'notification-recipients', businessId] });
    },
  });

  const primary = profile.data?.whatsappNumber;
  const additional = recipients.data ?? [];
  const isLoading = profile.isLoading || recipients.isLoading;

  return (
    <>
      <Card>
        <CardHeader>
          <div className="flex items-start justify-between gap-4">
            <div>
              <CardTitle>Notification recipients</CardTitle>
              <CardDescription>
                Everyone here gets a WhatsApp when a new lead lands. The primary number is from your business profile; add more for your team.
              </CardDescription>
            </div>
            <Button onClick={() => setOpen(true)}>
              <Plus className="h-4 w-4" /> Add recipient
            </Button>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="p-4 space-y-3">
              {Array.from({ length: 2 }).map((_, i) => (
                <Skeleton key={i} className="h-14" />
              ))}
            </div>
          ) : !primary && additional.length === 0 ? (
            <EmptyState
              icon={<MessageSquare className="h-5 w-5" />}
              title="No WhatsApp number set"
              description="Add a primary WhatsApp number in your business profile, then come back here to add teammates."
              className="py-10"
            />
          ) : (
            <ul className="divide-y">
              {primary && (
                <li className="flex items-center justify-between p-4">
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="grid h-9 w-9 place-items-center rounded-md bg-primary/10 text-primary">
                      <MessageSquare className="h-4 w-4" />
                    </div>
                    <div className="min-w-0">
                      <p className="font-mono text-sm font-medium">{primary}</p>
                      <p className="text-xs text-muted-foreground">Primary (from business profile)</p>
                    </div>
                  </div>
                  <Badge variant="success">Primary</Badge>
                </li>
              )}
              {additional.map((r) => (
                <li
                  key={r.id}
                  className="group flex items-center justify-between p-4 transition-colors hover:bg-accent/30"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="grid h-9 w-9 place-items-center rounded-md bg-muted text-muted-foreground">
                      <MessageSquare className="h-4 w-4" />
                    </div>
                    <div className="min-w-0">
                      <p className="font-mono text-sm font-medium">{r.whatsappNumber}</p>
                      <p className="text-xs text-muted-foreground truncate">
                        {r.label ?? 'Unlabeled'}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    {r.isActive ? (
                      <Badge variant="success">Active</Badge>
                    ) : (
                      <Badge variant="secondary">Inactive</Badge>
                    )}
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => {
                        if (confirm(`Remove ${r.whatsappNumber}?`)) remove.mutate(r.id);
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
    defaultValues: { whatsappNumber: '', label: '' },
  });

  const add = useMutation({
    mutationFn: (v: Values) =>
      business.addNotificationRecipient(businessId, {
        whatsappNumber: v.whatsappNumber.trim(),
        label: v.label?.trim() || undefined,
      }),
    onSuccess: () => {
      toast.success('Recipient added');
      qc.invalidateQueries({ queryKey: ['business', 'notification-recipients', businessId] });
      onOpenChange(false);
      form.reset();
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: { message?: string } } };
      toast.error(e?.response?.data?.message ?? 'Failed to add recipient');
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogHeader>
        <DialogTitle>Add notification recipient</DialogTitle>
      </DialogHeader>
      <form id="add-recipient-form" onSubmit={form.handleSubmit((v) => add.mutate(v))}>
        <DialogBody className="space-y-4">
          <div className="space-y-1.5">
            <Label>WhatsApp number</Label>
            <Input
              placeholder="+9198XXXXXXXX"
              autoComplete="off"
              {...form.register('whatsappNumber')}
            />
            <p className="text-xs text-muted-foreground">
              Use full international format with + and country code.
            </p>
            {form.formState.errors.whatsappNumber && (
              <p className="text-xs text-destructive">
                {form.formState.errors.whatsappNumber.message}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label>Label (optional)</Label>
            <Input placeholder="e.g. Manager, Reception" {...form.register('label')} />
          </div>
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="add-recipient-form" disabled={add.isPending}>
            {add.isPending ? 'Adding…' : 'Add recipient'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
