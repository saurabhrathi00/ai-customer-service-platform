import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { HelpCircle, Pencil, Plus, Trash2 } from 'lucide-react';

import { knowledge } from '@/api/resources';
import { Card, CardContent } from '@/components/ui/Card';
import { Skeleton } from '@/components/ui/Skeleton';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Switch } from '@/components/ui/Switch';
import { EmptyState } from '@/components/ui/EmptyState';
import { Dialog, DialogBody, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { useAuthStore } from '@/store/auth';
import type { FaqResponse } from '@/types/api';

const schema = z.object({
  question: z.string().min(1, 'Question is required').max(500),
  answer: z.string().min(1, 'Answer is required').max(2000),
  priority: z.number().int(),
  isActive: z.boolean(),
});
type Values = z.infer<typeof schema>;

export function FaqsTab() {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();
  const [editing, setEditing] = useState<FaqResponse | null>(null);
  const [open, setOpen] = useState(false);

  const faqs = useQuery({
    queryKey: ['knowledge', 'faqs', businessId],
    queryFn: () => knowledge.faqs(businessId),
  });

  const removeMutation = useMutation({
    mutationFn: (faqId: string) => knowledge.deleteFaq(businessId, faqId),
    onSuccess: () => {
      toast.success('FAQ deleted');
      qc.invalidateQueries({ queryKey: ['knowledge', 'faqs', businessId] });
    },
  });

  function openNew() {
    setEditing(null);
    setOpen(true);
  }
  function openEdit(f: FaqResponse) {
    setEditing(f);
    setOpen(true);
  }

  return (
    <>
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold">Frequently asked questions</h3>
          <p className="text-sm text-muted-foreground">
            High-precision answers your AI uses verbatim. Up to 50 active.
          </p>
        </div>
        <Button onClick={openNew}>
          <Plus className="h-4 w-4" /> New FAQ
        </Button>
      </div>

      <Card>
        <CardContent className="p-0">
          {faqs.isLoading ? (
            <div className="p-4 space-y-3">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-16" />
              ))}
            </div>
          ) : (faqs.data?.length ?? 0) === 0 ? (
            <EmptyState
              icon={<HelpCircle className="h-5 w-5" />}
              title="No FAQs yet"
              description="Add the questions your customers ask the most. Your AI will quote these answers verbatim."
              action={
                <Button onClick={openNew}>
                  <Plus className="h-4 w-4" /> Add first FAQ
                </Button>
              }
            />
          ) : (
            <ul className="divide-y">
              {faqs.data!
                .slice()
                .sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0))
                .map((f) => (
                  <li key={f.id} className="group p-4 transition-all duration-200 hover:bg-primary/[0.04] hover:translate-y-[-1px] hover:shadow-[0_0_0_1px_hsl(var(--primary)/0.08),0_4px_12px_hsl(0_0%_0%/0.1)]">
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0 flex-1">
                        <p className="font-medium leading-6">{f.question}</p>
                        <p className="mt-1 text-sm text-muted-foreground whitespace-pre-wrap">
                          {f.answer}
                        </p>
                        <div className="mt-2 flex items-center gap-2">
                          {!f.isActive && <Badge variant="secondary">Disabled</Badge>}
                          {(f.priority ?? 0) > 0 && (
                            <Badge variant="outline">Priority {f.priority}</Badge>
                          )}
                        </div>
                      </div>
                      <div className="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(f)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            if (confirm('Delete this FAQ?')) removeMutation.mutate(f.id);
                          }}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>
                    </div>
                  </li>
                ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <FaqDialog open={open} onOpenChange={setOpen} faq={editing} />
    </>
  );
}

function FaqDialog({
  open,
  onOpenChange,
  faq,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  faq: FaqResponse | null;
}) {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: {
      question: faq?.question ?? '',
      answer: faq?.answer ?? '',
      priority: faq?.priority ?? 0,
      isActive: faq?.isActive ?? true,
    },
  });

  const mutation = useMutation({
    mutationFn: (v: Values) =>
      faq
        ? knowledge.updateFaq(businessId, faq.id, v)
        : knowledge.addFaq(businessId, v),
    onSuccess: () => {
      toast.success(faq ? 'FAQ updated' : 'FAQ added');
      qc.invalidateQueries({ queryKey: ['knowledge', 'faqs', businessId] });
      onOpenChange(false);
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogHeader>
        <DialogTitle>{faq ? 'Edit FAQ' : 'New FAQ'}</DialogTitle>
      </DialogHeader>
      <form
        id="faq-form"
        onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
        className="contents"
      >
        <DialogBody className="space-y-4">
          <div className="space-y-1.5">
            <Label>Question</Label>
            <Input
              placeholder="What are your timings?"
              {...form.register('question')}
            />
            {form.formState.errors.question && (
              <p className="text-xs text-destructive">
                {form.formState.errors.question.message}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label>Answer</Label>
            <Textarea
              rows={5}
              placeholder="We're open Mon–Sat from 9am to 7pm."
              {...form.register('answer')}
            />
            {form.formState.errors.answer && (
              <p className="text-xs text-destructive">{form.formState.errors.answer.message}</p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label>Priority</Label>
              <Input
                type="number"
                {...form.register('priority', { valueAsNumber: true })}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Active</Label>
              <div className="flex h-10 items-center gap-2">
                <Switch
                  checked={form.watch('isActive') ?? true}
                  onCheckedChange={(v) => form.setValue('isActive', v, { shouldDirty: true })}
                />
                <span className="text-sm text-muted-foreground">
                  {form.watch('isActive') ?? true ? 'Visible to AI' : 'Hidden'}
                </span>
              </div>
            </div>
          </div>
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="faq-form" loading={mutation.isPending}>
            {faq ? 'Save' : 'Add FAQ'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
