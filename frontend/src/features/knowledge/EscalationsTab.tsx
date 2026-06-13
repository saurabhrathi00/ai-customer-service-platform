import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import {
  AlertTriangle,
  Pencil,
  Plus,
  Shield,
  PhoneForwarded,
  XCircle,
  Trash2,
} from 'lucide-react';

import { knowledge } from '@/api/resources';
import { Card, CardContent } from '@/components/ui/Card';
import { Skeleton } from '@/components/ui/Skeleton';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Select } from '@/components/ui/Select';
import { Switch } from '@/components/ui/Switch';
import { EmptyState } from '@/components/ui/EmptyState';
import { Dialog, DialogBody, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { useAuthStore } from '@/store/auth';
import type { EscalationAction, EscalationRuleResponse } from '@/types/api';

const schema = z.object({
  triggerPhrase: z.string().min(1, 'Trigger is required').max(500),
  action: z.enum(['TRANSFER', 'CALLBACK', 'DECLINE']),
  actionMessage: z.string().max(1000).optional().or(z.literal('')),
  isActive: z.boolean(),
});
type Values = z.infer<typeof schema>;

const ACTION_META: Record<EscalationAction, { label: string; icon: React.ReactNode; variant: 'default' | 'warning' | 'destructive' }> = {
  TRANSFER: { label: 'Transfer', icon: <PhoneForwarded className="h-3.5 w-3.5" />, variant: 'default' },
  CALLBACK: { label: 'Callback', icon: <Shield className="h-3.5 w-3.5" />, variant: 'warning' },
  DECLINE:  { label: 'Decline',  icon: <XCircle className="h-3.5 w-3.5" />,        variant: 'destructive' },
};

export function EscalationsTab() {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<EscalationRuleResponse | null>(null);

  const rules = useQuery({
    queryKey: ['knowledge', 'escalations', businessId],
    queryFn: () => knowledge.escalations(businessId),
  });

  const removeMutation = useMutation({
    mutationFn: (ruleId: string) => knowledge.deleteEscalation(businessId, ruleId),
    onSuccess: () => {
      toast.success('Rule removed');
      qc.invalidateQueries({ queryKey: ['knowledge', 'escalations', businessId] });
    },
  });

  return (
    <>
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold">Escalation rules</h3>
          <p className="text-sm text-muted-foreground">
            When a caller says certain things, take action instead of answering.
          </p>
        </div>
        <Button
          onClick={() => {
            setEditing(null);
            setOpen(true);
          }}
        >
          <Plus className="h-4 w-4" /> New rule
        </Button>
      </div>

      <Card>
        <CardContent className="p-0">
          {rules.isLoading ? (
            <div className="p-4 space-y-3">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-16" />
              ))}
            </div>
          ) : (rules.data?.length ?? 0) === 0 ? (
            <EmptyState
              icon={<AlertTriangle className="h-5 w-5" />}
              title="No escalation rules"
              description="Add a rule like 'speak to a manager' → Transfer, so the AI knows when to step aside."
              action={
                <Button
                  onClick={() => {
                    setEditing(null);
                    setOpen(true);
                  }}
                >
                  <Plus className="h-4 w-4" /> Add first rule
                </Button>
              }
            />
          ) : (
            <ul className="divide-y">
              {rules.data!.map((r) => {
                const meta = ACTION_META[r.action];
                return (
                  <li key={r.id} className="group p-4 transition-all duration-200 hover:bg-primary/[0.04] hover:translate-y-[-1px] hover:shadow-[0_0_0_1px_hsl(var(--primary)/0.08),0_4px_12px_hsl(0_0%_0%/0.1)]">
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0 flex-1">
                        <p className="font-medium leading-6">"{r.triggerPhrase}"</p>
                        {r.actionMessage && (
                          <p className="mt-1 text-sm text-muted-foreground">
                            Bot says: <span className="italic">"{r.actionMessage}"</span>
                          </p>
                        )}
                        <div className="mt-2 flex items-center gap-2">
                          <Badge variant={meta.variant}>
                            {meta.icon}
                            <span className="ml-1">{meta.label}</span>
                          </Badge>
                          {!r.isActive && <Badge variant="secondary">Disabled</Badge>}
                        </div>
                      </div>
                      <div className="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setEditing(r);
                            setOpen(true);
                          }}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            if (confirm('Delete this rule?')) removeMutation.mutate(r.id);
                          }}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </CardContent>
      </Card>

      <RuleDialog open={open} onOpenChange={setOpen} rule={editing} />
    </>
  );
}

function RuleDialog({
  open,
  onOpenChange,
  rule,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  rule: EscalationRuleResponse | null;
}) {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: {
      triggerPhrase: rule?.triggerPhrase ?? '',
      action: rule?.action ?? 'CALLBACK',
      actionMessage: rule?.actionMessage ?? '',
      isActive: rule?.isActive ?? true,
    },
  });

  const mutation = useMutation({
    mutationFn: (v: Values) => {
      const body = { ...v, actionMessage: v.actionMessage || undefined };
      return rule
        ? knowledge.updateEscalation(businessId, rule.id, body)
        : knowledge.addEscalation(businessId, body);
    },
    onSuccess: () => {
      toast.success(rule ? 'Rule updated' : 'Rule added');
      qc.invalidateQueries({ queryKey: ['knowledge', 'escalations', businessId] });
      onOpenChange(false);
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogHeader>
        <DialogTitle>{rule ? 'Edit rule' : 'New escalation rule'}</DialogTitle>
      </DialogHeader>
      <form id="rule-form" onSubmit={form.handleSubmit((v) => mutation.mutate(v))}>
        <DialogBody className="space-y-4">
          <div className="space-y-1.5">
            <Label>Trigger phrase</Label>
            <Input
              placeholder="speak to a manager"
              {...form.register('triggerPhrase')}
            />
            {form.formState.errors.triggerPhrase && (
              <p className="text-xs text-destructive">
                {form.formState.errors.triggerPhrase.message}
              </p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label>Action</Label>
              <Select {...form.register('action')}>
                <option value="TRANSFER">Transfer the call</option>
                <option value="CALLBACK">Request a callback</option>
                <option value="DECLINE">Politely decline</option>
              </Select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Active</Label>
              <div className="flex h-10 items-center gap-2">
                <Switch
                  checked={form.watch('isActive') ?? true}
                  onCheckedChange={(v) => form.setValue('isActive', v, { shouldDirty: true })}
                />
                <span className="text-sm text-muted-foreground">
                  {form.watch('isActive') ?? true ? 'On' : 'Off'}
                </span>
              </div>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Message (optional)</Label>
            <Textarea
              rows={3}
              placeholder="What the bot says before taking the action."
              {...form.register('actionMessage')}
            />
          </div>
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="rule-form" loading={mutation.isPending}>
            {rule ? 'Save' : 'Add rule'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
