import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { format } from 'date-fns';
import { Save, FileText } from 'lucide-react';

import { knowledge } from '@/api/resources';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Skeleton } from '@/components/ui/Skeleton';
import { Button } from '@/components/ui/Button';
import { Textarea } from '@/components/ui/Input';
import { useAuthStore } from '@/store/auth';

export function FreeformTab() {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();
  const [draft, setDraft] = useState<string>('');

  const ff = useQuery({
    queryKey: ['knowledge', 'freeform', businessId],
    queryFn: () => knowledge.freeform(businessId),
  });

  useEffect(() => {
    if (ff.data?.content != null) setDraft(ff.data.content);
  }, [ff.data?.content]);

  const mutation = useMutation({
    mutationFn: () => knowledge.updateFreeform(businessId, draft),
    onSuccess: () => {
      toast.success('Notes saved');
      qc.invalidateQueries({ queryKey: ['knowledge', 'freeform', businessId] });
    },
  });

  const dirty = draft !== (ff.data?.content ?? '');
  const charsLeft = 10_000 - draft.length;

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle className="flex items-center gap-2">
              <FileText className="h-4 w-4" /> Free-form notes
            </CardTitle>
            <CardDescription>
              Anything else the AI should know — written like you're briefing a new hire.
            </CardDescription>
          </div>
          {ff.data?.updatedAt && (
            <span className="text-xs text-muted-foreground whitespace-nowrap">
              Updated {format(new Date(ff.data.updatedAt), 'MMM d, HH:mm')}
            </span>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {ff.isLoading ? (
          <Skeleton className="h-64" />
        ) : (
          <Textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            rows={14}
            maxLength={10_000}
            placeholder="Tone, brand voice, things to avoid, regional context, current promotions, anything else the AI should reference…"
            className="font-mono text-sm leading-6"
          />
        )}
        <div className="flex items-center justify-between">
          <span
            className={`text-xs ${charsLeft < 200 ? 'text-destructive' : 'text-muted-foreground'}`}
          >
            {charsLeft.toLocaleString()} characters left
          </span>
          <Button
            onClick={() => mutation.mutate()}
            disabled={!dirty}
            loading={mutation.isPending}
          >
            <Save className="h-4 w-4" /> Save
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
