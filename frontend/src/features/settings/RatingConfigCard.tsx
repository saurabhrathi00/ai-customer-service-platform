import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Star } from 'lucide-react';

import { business } from '@/api/resources';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { useAuthStore } from '@/store/auth';
import type { RatingSignalKey } from '@/types/api';

const LABELS: Record<RatingSignalKey, { label: string; help: string }> = {
  LONG_CALL:           { label: 'Long call', help: 'Caller stayed engaged for a while' },
  POSITIVE_FEEDBACK:   { label: 'Positive feedback', help: 'Customer pressed 1 after the call' },
  CALLBACK_REQUESTED:  { label: 'Callback requested', help: 'Customer asked you to call back' },
  NEGATIVE_FEEDBACK:   { label: 'Negative feedback', help: 'Customer pressed 2 after the call' },
  SHORT_CALL:          { label: 'Short call', help: 'Caller hung up almost immediately' },
  AI_COULD_NOT_ANSWER: { label: 'AI could not answer', help: 'Question landed outside the knowledge' },
};

export function RatingConfigCard() {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();
  const [draft, setDraft] = useState<Partial<Record<RatingSignalKey, number>>>({});

  const config = useQuery({
    queryKey: ['business', 'rating-config', businessId],
    queryFn: () => business.ratingConfig(businessId),
  });

  useEffect(() => {
    if (!config.data) return;
    const next: Partial<Record<RatingSignalKey, number>> = {};
    for (const e of config.data.entries) next[e.signalKey] = e.scoreValue;
    setDraft(next);
  }, [config.data]);

  const save = useMutation({
    mutationFn: () =>
      business.updateRatingConfig(
        businessId,
        Object.entries(draft).map(([signalKey, scoreValue]) => ({
          signalKey: signalKey as RatingSignalKey,
          scoreValue: scoreValue ?? 0,
        })),
      ),
    onSuccess: () => {
      toast.success('Rating signals updated');
      qc.invalidateQueries({ queryKey: ['business', 'rating-config', businessId] });
    },
  });

  const dirty =
    config.data &&
    config.data.entries.some((e) => draft[e.signalKey] !== e.scoreValue);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Star className="h-4 w-4 text-[hsl(var(--warning))]" /> Interest scoring
        </CardTitle>
        <CardDescription>
          How much each signal contributes to a call's interest rating.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {config.isLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-14" />
            ))}
          </div>
        ) : (
          <div className="space-y-3">
            {(Object.keys(LABELS) as RatingSignalKey[]).map((key) => {
              const meta = LABELS[key];
              const present = config.data?.entries.find((e) => e.signalKey === key);
              return (
                <div
                  key={key}
                  className="flex items-center justify-between gap-4 rounded-lg border p-3"
                >
                  <div className="min-w-0">
                    <Label className="block">{meta.label}</Label>
                    <p className="text-xs text-muted-foreground mt-0.5">{meta.help}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Input
                      type="number"
                      className="w-24 text-right font-mono"
                      value={draft[key] ?? present?.scoreValue ?? 0}
                      onChange={(e) =>
                        setDraft({ ...draft, [key]: Number(e.target.value) })
                      }
                    />
                  </div>
                </div>
              );
            })}
            <div className="flex justify-end">
              <Button
                onClick={() => save.mutate()}
                disabled={!dirty}
                loading={save.isPending}
              >
                Save changes
              </Button>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
