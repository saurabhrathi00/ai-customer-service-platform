import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Bell, Save, Star } from 'lucide-react';
import { motion } from 'framer-motion';

import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Select } from '@/components/ui/Select';
import { Skeleton } from '@/components/ui/Skeleton';
import { Badge } from '@/components/ui/Badge';
import { leadSettings } from '@/api/resources';
import { useAuthStore } from '@/store/auth';
import type { ReminderMode } from '@/types/api';

const MAX_INTERVAL_MIN = 240;

export default function ConfigurationsPage() {
  const businessId = useAuthStore((s) => s.businessId)!;
  const qc = useQueryClient();

  const q = useQuery({
    queryKey: ['lead-settings', businessId],
    queryFn: () => leadSettings.get(businessId),
  });

  const [threshold, setThreshold] = useState(7);
  const [mode, setMode] = useState<ReminderMode>('FIXED');
  const [interval, setInterval] = useState(15);
  const [maxN, setMaxN] = useState(10);

  useEffect(() => {
    if (q.data) {
      setThreshold(Number(q.data.highInterestThreshold));
      setMode(q.data.reminderMode);
      setInterval(q.data.reminderIntervalMinutes);
      setMaxN(q.data.maxReminders);
    }
  }, [q.data]);

  const save = useMutation({
    mutationFn: () =>
      leadSettings.update(businessId, {
        highInterestThreshold: threshold,
        reminderMode: mode,
        reminderIntervalMinutes: interval,
        maxReminders: maxN,
      }),
    onSuccess: () => {
      toast.success('Configuration saved');
      qc.invalidateQueries({ queryKey: ['lead-settings', businessId] });
    },
  });

  const dirty = q.data && (
    Number(q.data.highInterestThreshold) !== threshold
    || q.data.reminderMode !== mode
    || q.data.reminderIntervalMinutes !== interval
    || q.data.maxReminders !== maxN
  );

  const previewSchedule = computePreview(mode, interval, maxN);

  return (
    <>
      <PageHeader
        title="Configurations"
        subtitle="Tune when leads fire and how aggressively we remind you."
      />
      <PageBody className="grid gap-6 lg:grid-cols-2">
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut', delay: 0 }}
        >
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Star className="h-4 w-4 text-[hsl(var(--warning))]" /> Interest threshold
              </CardTitle>
              <CardDescription>
                Calls whose AI-rated interest meets this score become a HIGH_INTEREST lead. Lower = more leads, higher = stricter.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {q.isLoading ? (
                <Skeleton className="h-16" />
              ) : (
                <div className="space-y-3">
                  <div className="flex items-center gap-4">
                    <Input
                      type="range"
                      min={0}
                      max={10}
                      step={0.5}
                      value={threshold}
                      onChange={(e) => setThreshold(Number(e.target.value))}
                      className="flex-1"
                    />
                    <span className="w-12 text-right text-2xl font-semibold tabular-nums">
                      {threshold.toFixed(1)}
                    </span>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    At <b>{threshold.toFixed(1)}/10</b>, roughly the {bucketLabel(threshold)} of warm callers will trigger a lead.
                  </p>
                </div>
              )}
            </CardContent>
          </Card>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut', delay: 0.08 }}
        >
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Bell className="h-4 w-4 text-primary" /> Reminder cadence
              </CardTitle>
              <CardDescription>
                How often we ping your WhatsApp until you act on a pending lead.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {q.isLoading ? (
                <Skeleton className="h-40" />
              ) : (
                <>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div className="space-y-1.5">
                      <Label>Mode</Label>
                      <Select value={mode} onChange={(e) => setMode(e.target.value as ReminderMode)}>
                        <option value="FIXED">Fixed (every N min)</option>
                        <option value="INCREMENT">Increment (back off over time)</option>
                      </Select>
                    </div>
                    <div className="space-y-1.5">
                      <Label>Base interval (minutes)</Label>
                      <Input
                        type="number"
                        min={1}
                        max={MAX_INTERVAL_MIN}
                        value={interval}
                        onChange={(e) => setInterval(clamp(Number(e.target.value) || 1, 1, MAX_INTERVAL_MIN))}
                      />
                    </div>
                  </div>
                  <div className="space-y-1.5">
                    <Label>Max reminders (hard cap)</Label>
                    <Input
                      type="number"
                      min={1}
                      max={10}
                      value={maxN}
                      onChange={(e) => setMaxN(clamp(Number(e.target.value) || 1, 1, 10))}
                    />
                  </div>
                  <div
                    className="rounded-xl border border-primary/15 bg-primary/5 p-3 backdrop-blur-sm"
                    style={{ boxShadow: '0 0 0 1px hsl(252 90% 67% / 0.06), 0 4px 16px hsl(252 90% 67% / 0.04)' }}
                  >
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                      Schedule preview
                    </p>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {previewSchedule.map((m, i) => (
                        <Badge key={i} variant="outline" className="border-primary/20 text-primary">
                          +{m}m
                        </Badge>
                      ))}
                    </div>
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        </motion.div>

        <motion.div
          className="lg:col-span-2 flex justify-end"
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: 'easeOut', delay: 0.18 }}
        >
          <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!dirty}>
            <Save className="h-4 w-4" /> Save configuration
          </Button>
        </motion.div>
      </PageBody>
    </>
  );
}

function clamp(n: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, n));
}

function bucketLabel(t: number) {
  if (t >= 9) return 'top 5%';
  if (t >= 7) return 'top 25%';
  if (t >= 5) return 'top half';
  return 'majority';
}

function computePreview(mode: ReminderMode, interval: number, maxN: number): number[] {
  const out: number[] = [];
  let elapsed = 0;
  for (let i = 0; i < maxN; i++) {
    const step = mode === 'FIXED' ? interval : interval * (i + 1);
    elapsed += step;
    out.push(elapsed);
  }
  return out;
}
