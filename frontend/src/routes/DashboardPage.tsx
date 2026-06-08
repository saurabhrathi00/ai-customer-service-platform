import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  PhoneCall,
  PhoneForwarded,
  TrendingUp,
  Star,
  Clock,
  ArrowUpRight,
  Play,
  Gauge,
  CalendarDays,
  Timer,
} from 'lucide-react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from 'recharts';
import { format, startOfDay, subDays } from 'date-fns';

import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Skeleton } from '@/components/ui/Skeleton';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { Button } from '@/components/ui/Button';
import { LiveDemoModal } from '@/features/demo/LiveDemoModal';
import { calls, business, subscription } from '@/api/resources';
import { useAuthStore, isAdmin } from '@/store/auth';
import {
  callDuration,
  callRelative,
  feedbackLabel,
  interestColor,
} from '@/features/calls/helpers';
import { useSummariesByCallId } from '@/features/calls/useSummaries';
import { formatDuration } from '@/lib/utils';

export default function DashboardPage() {
  const businessId = useAuthStore((s) => s.businessId);
  const [demoOpen, setDemoOpen] = useState(false);

  const profile = useQuery({
    queryKey: ['business', 'profile', businessId],
    queryFn: () => business.profile(businessId!),
    enabled: Boolean(businessId),
  });

  const recent = useQuery({
    queryKey: ['calls', 'recent', businessId],
    queryFn: () => calls.recent(businessId!),
    enabled: Boolean(businessId),
    refetchInterval: 60_000,
  });

  const sub = useQuery({
    queryKey: ['subscription', businessId],
    queryFn: () => subscription.current(businessId!),
    enabled: Boolean(businessId),
    retry: false,
  });

  const { map: summaryMap } = useSummariesByCallId();
  const stats = useMemo(
    () => computeStats(recent.data ?? [], summaryMap),
    [recent.data, summaryMap],
  );

  const demoSeconds = profile.data?.liveDemoSecondsRemaining ?? 0;

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle="A quick read on your call activity over the past two weeks."
        actions={
          isAdmin() ? (
            <Button onClick={() => setDemoOpen(true)} className="gap-2">
              <Play className="h-4 w-4" />
              Try Live Demo
            </Button>
          ) : undefined
        }
      />

      <LiveDemoModal
        open={demoOpen}
        onOpenChange={setDemoOpen}
        demoSecondsRemaining={demoSeconds}
      />
      <PageBody className="space-y-6">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <KpiCard
            label="Calls today"
            value={recent.isLoading ? null : String(stats.today)}
            delta={stats.todayDelta}
            icon={PhoneCall}
            tone="primary"
          />
          <KpiCard
            label="Calls this week"
            value={recent.isLoading ? null : String(stats.week)}
            delta={stats.weekDelta}
            icon={TrendingUp}
            tone="default"
          />
          <KpiCard
            label="Callbacks pending"
            value={recent.isLoading ? null : String(stats.callbacks)}
            icon={PhoneForwarded}
            tone="warning"
            hint={stats.callbacks > 0 ? 'Work the queue →' : undefined}
            hintTo="/calls?filter=callbacks"
          />
          <KpiCard
            label="Avg interest"
            value={recent.isLoading ? null : stats.avgInterest != null ? stats.avgInterest.toFixed(1) : '—'}
            icon={Star}
            tone="success"
          />
        </div>

        {sub.data?.plan ? (
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>Plan Usage</CardTitle>
                  <CardDescription>
                    {sub.data.plan.name} plan — {sub.data.daysRemaining} days remaining
                  </CardDescription>
                </div>
                <Link
                  to="/settings"
                  className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
                >
                  Manage <ArrowUpRight className="h-4 w-4" />
                </Link>
              </div>
            </CardHeader>
            <CardContent>
              <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
                <UsageMeter
                  label="Calls used"
                  used={sub.data.callsUsed}
                  total={sub.data.plan.callsIncluded}
                  unit="calls"
                  icon={PhoneCall}
                />
                <UsageMeter
                  label="Minutes used"
                  used={sub.data.minutesUsed}
                  total={null}
                  unit="min"
                  icon={Timer}
                />
                <UsageItem
                  label="Calls remaining"
                  value={String(sub.data.callsRemaining)}
                  icon={Gauge}
                  tone={sub.data.callsRemaining <= 10 ? 'warning' : 'success'}
                />
                <UsageItem
                  label="Days remaining"
                  value={String(sub.data.daysRemaining)}
                  icon={CalendarDays}
                  tone={sub.data.daysRemaining <= 5 ? 'warning' : 'default'}
                />
              </div>
            </CardContent>
          </Card>
        ) : !sub.isLoading && (
          <Card>
            <CardContent className="flex items-center justify-between p-6">
              <div>
                <p className="font-semibold">No active plan</p>
                <p className="text-sm text-muted-foreground">
                  Subscribe to a plan to start using your AI voice agent.
                </p>
              </div>
              <Link to="/pricing">
                <Button>View Plans</Button>
              </Link>
            </CardContent>
          </Card>
        )}

        <div className="grid gap-4 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>Calls / day</CardTitle>
                  <CardDescription>Last 14 days</CardDescription>
                </div>
                <Badge variant="outline">{recent.data?.length ?? 0} total</Badge>
              </div>
            </CardHeader>
            <CardContent>
              {recent.isLoading ? (
                <Skeleton className="h-64" />
              ) : stats.series.every((d) => d.count === 0) ? (
                <EmptyState
                  icon={<PhoneCall className="h-5 w-5" />}
                  title="No calls yet"
                  description="When customers start ringing your AI number, you'll see the activity here."
                />
              ) : (
                <div className="h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={stats.series} margin={{ left: -20, right: 8, top: 10 }}>
                      <defs>
                        <linearGradient id="callsGradient" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="hsl(262 83% 58%)" stopOpacity={0.35} />
                          <stop offset="100%" stopColor="hsl(262 83% 58%)" stopOpacity={0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                      <XAxis
                        dataKey="label"
                        tick={{ fontSize: 12, fill: 'hsl(var(--muted-foreground))' }}
                        tickLine={false}
                        axisLine={false}
                      />
                      <YAxis
                        allowDecimals={false}
                        tick={{ fontSize: 12, fill: 'hsl(var(--muted-foreground))' }}
                        tickLine={false}
                        axisLine={false}
                        width={32}
                      />
                      <Tooltip content={<ChartTooltip />} />
                      <Area
                        type="monotone"
                        dataKey="count"
                        stroke="hsl(262 83% 58%)"
                        strokeWidth={2}
                        fill="url(#callsGradient)"
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Quick stats</CardTitle>
              <CardDescription>Snapshot of recent activity</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <StatRow label="Avg duration" value={formatDuration(stats.avgDuration)} icon={Clock} />
              <StatRow
                label="Helpful feedback"
                value={`${stats.helpfulPct ?? 0}%`}
                icon={Star}
              />
              <StatRow
                label="Hot leads (≥4 ★)"
                value={String(stats.hotLeads)}
                icon={TrendingUp}
              />
              <StatRow label="Total calls" value={String(stats.total)} icon={PhoneCall} />
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>Recent calls</CardTitle>
                <CardDescription>The five most recent conversations</CardDescription>
              </div>
              <Link
                to="/calls"
                className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
              >
                View all <ArrowUpRight className="h-4 w-4" />
              </Link>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {recent.isLoading ? (
              <div className="p-4 space-y-3">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Skeleton key={i} className="h-14" />
                ))}
              </div>
            ) : (recent.data?.length ?? 0) === 0 ? (
              <EmptyState
                icon={<PhoneCall className="h-5 w-5" />}
                title="No calls yet"
                description="Once your phone number is wired up, every call lands here within seconds of ending."
              />
            ) : (
              <ul className="divide-y">
                {recent.data!.slice(0, 5).map((c) => {
                  const fb = feedbackLabel(c.feedbackScore);
                  const s = summaryMap.get(c.id);
                  const summaryText = s?.summaryText ?? c.callSummary;
                  const interest = s?.interestRating ?? c.interestRating;
                  const callback = s?.callbackNeeded ?? c.callbackRequested;
                  return (
                    <li key={c.id}>
                      <Link
                        to={`/calls/${c.id}`}
                        className="flex items-center gap-4 px-4 py-3 transition-colors hover:bg-accent/40"
                      >
                        <div className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-primary/10 text-primary">
                          <PhoneCall className="h-4 w-4" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium">
                            {s?.callerName ?? c.customerName ?? c.customerPhone ?? 'Anonymous caller'}
                          </p>
                          <p className="truncate text-xs text-muted-foreground">
                            {summaryText ?? 'Summary pending…'}
                          </p>
                        </div>
                        <div className="hidden sm:flex flex-col items-end gap-1">
                          <span className="text-xs text-muted-foreground">
                            {callRelative(c.callStartedAt)} · {formatDuration(callDuration(c))}
                          </span>
                          <div className="flex items-center gap-1.5">
                            {callback && <Badge variant="warning">Callback</Badge>}
                            {interest != null && (
                              <Badge variant={interestColor(interest) as never}>
                                {interest}/10 ★
                              </Badge>
                            )}
                            {fb && <Badge variant={fb.variant}>{fb.label}</Badge>}
                          </div>
                        </div>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            )}
          </CardContent>
        </Card>
      </PageBody>
    </>
  );
}

interface KpiProps {
  label: string;
  value: string | null;
  delta?: number;
  icon: React.ComponentType<{ className?: string }>;
  tone: 'primary' | 'default' | 'success' | 'warning';
  hint?: string;
  hintTo?: string;
}
function KpiCard({ label, value, delta, icon: Icon, tone, hint, hintTo }: KpiProps) {
  const toneClass: Record<KpiProps['tone'], string> = {
    primary: 'bg-primary/10 text-primary',
    default: 'bg-accent text-accent-foreground',
    success: 'bg-[hsl(var(--success))]/10 text-[hsl(var(--success))]',
    warning: 'bg-[hsl(var(--warning))]/10 text-[hsl(var(--warning))]',
  };
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between">
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          <div className={`grid h-9 w-9 place-items-center rounded-lg ${toneClass[tone]}`}>
            <Icon className="h-4 w-4" />
          </div>
        </div>
        <div className="mt-3 flex items-baseline gap-2">
          {value === null ? (
            <Skeleton className="h-8 w-16" />
          ) : (
            <span className="text-3xl font-semibold tracking-tight">{value}</span>
          )}
          {delta != null && delta !== 0 && (
            <span
              className={`text-xs font-medium ${delta > 0 ? 'text-[hsl(var(--success))]' : 'text-destructive'}`}
            >
              {delta > 0 ? '+' : ''}
              {delta}
            </span>
          )}
        </div>
        {hint && hintTo && (
          <Link
            to={hintTo}
            className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
          >
            {hint}
          </Link>
        )}
      </CardContent>
    </Card>
  );
}

function StatRow({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: string;
  icon: React.ComponentType<{ className?: string }>;
}) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Icon className="h-4 w-4" />
        {label}
      </div>
      <span className="text-sm font-semibold">{value}</span>
    </div>
  );
}

interface ChartPayload { value: number }
interface ChartTooltipProps {
  active?: boolean;
  label?: string;
  payload?: ChartPayload[];
}
function ChartTooltip({ active, label, payload }: ChartTooltipProps) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-md border bg-popover px-2.5 py-1.5 text-xs shadow-md bg-card">
      <p className="font-medium">{label}</p>
      <p className="text-muted-foreground">{payload[0].value} calls</p>
    </div>
  );
}

function UsageMeter({
  label,
  used,
  total,
  unit,
  icon: Icon,
}: {
  label: string;
  used: number;
  total: number | null;
  unit: string;
  icon: React.ComponentType<{ className?: string }>;
}) {
  const pct = total ? Math.min(100, Math.round((used / total) * 100)) : null;
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Icon className="h-4 w-4" />
        {label}
      </div>
      <p className="text-2xl font-semibold tracking-tight">
        {used}
        {total != null && <span className="text-base font-normal text-muted-foreground">/{total}</span>}
        <span className="ml-1 text-sm font-normal text-muted-foreground">{unit}</span>
      </p>
      {pct != null && (
        <div className="h-2 w-full rounded-full bg-muted">
          <div
            className={`h-full rounded-full transition-all ${
              pct >= 90 ? 'bg-red-500' : pct >= 70 ? 'bg-yellow-500' : 'bg-primary'
            }`}
            style={{ width: `${pct}%` }}
          />
        </div>
      )}
    </div>
  );
}

function UsageItem({
  label,
  value,
  icon: Icon,
  tone,
}: {
  label: string;
  value: string;
  icon: React.ComponentType<{ className?: string }>;
  tone: 'default' | 'success' | 'warning';
}) {
  const toneText: Record<typeof tone, string> = {
    default: '',
    success: 'text-[hsl(var(--success))]',
    warning: 'text-[hsl(var(--warning))]',
  };
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Icon className="h-4 w-4" />
        {label}
      </div>
      <p className={`text-2xl font-semibold tracking-tight ${toneText[tone]}`}>{value}</p>
    </div>
  );
}

function computeStats(
  callsList: import('@/types/api').CallLogResponse[],
  summaryMap: Map<string, import('@/types/api').CallSummaryResponse>,
) {
  const now = new Date();
  const today = startOfDay(now);
  const yesterday = startOfDay(subDays(now, 1));
  const weekStart = startOfDay(subDays(now, 6));
  const prevWeekStart = startOfDay(subDays(now, 13));

  let todayCount = 0;
  let yesterdayCount = 0;
  let weekCount = 0;
  let prevWeekCount = 0;
  let callbacks = 0;
  let totalDuration = 0;
  let durationN = 0;
  let interestSum = 0;
  let interestN = 0;
  let hotLeads = 0;
  let helpful = 0;
  let feedbackN = 0;

  const dayBuckets = new Map<string, number>();
  for (let i = 13; i >= 0; i--) {
    const d = startOfDay(subDays(now, i));
    dayBuckets.set(format(d, 'MMM d'), 0);
  }

  for (const c of callsList) {
    const d = new Date(c.callStartedAt);
    const dayLabel = format(startOfDay(d), 'MMM d');
    if (dayBuckets.has(dayLabel)) {
      dayBuckets.set(dayLabel, (dayBuckets.get(dayLabel) ?? 0) + 1);
    }
    if (d >= today) todayCount++;
    else if (d >= yesterday) yesterdayCount++;
    if (d >= weekStart) weekCount++;
    else if (d >= prevWeekStart) prevWeekCount++;

    const s = summaryMap.get(c.id);
    if (s?.callbackNeeded ?? c.callbackRequested) callbacks++;

    const dur = callDuration(c);
    if (dur != null) {
      totalDuration += dur;
      durationN++;
    }

    const interest = s?.interestRating ?? c.interestRating;
    if (interest != null) {
      interestSum += interest;
      interestN++;
      if (interest >= 4) hotLeads++;
    }

    if (c.feedbackScore != null) {
      feedbackN++;
      if (c.feedbackScore === 1) helpful++;
    }
  }

  return {
    today: todayCount,
    todayDelta: todayCount - yesterdayCount,
    week: weekCount,
    weekDelta: weekCount - prevWeekCount,
    callbacks,
    total: callsList.length,
    avgDuration: durationN ? Math.round(totalDuration / durationN) : null,
    avgInterest: interestN ? interestSum / interestN : null,
    hotLeads,
    helpfulPct: feedbackN ? Math.round((helpful / feedbackN) * 100) : null,
    series: Array.from(dayBuckets, ([label, count]) => ({ label, count })),
  };
}
