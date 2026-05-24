import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PhoneCall, Search, PhoneForwarded, Star } from 'lucide-react';

import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { Card, CardContent } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Skeleton } from '@/components/ui/Skeleton';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { calls } from '@/api/resources';
import { useAuthStore } from '@/store/auth';
import { callDuration, callRelative, feedbackLabel, interestColor } from '@/features/calls/helpers';
import { formatDuration } from '@/lib/utils';

type FilterKey = 'all' | 'callbacks' | 'hot';

export default function CallsListPage() {
  const [params, setParams] = useSearchParams();
  const businessId = useAuthStore((s) => s.businessId);
  const filterParam = (params.get('filter') as FilterKey) || 'all';
  const [filter, setFilter] = useState<FilterKey>(filterParam);
  const [q, setQ] = useState('');

  const recent = useQuery({
    queryKey: ['calls', 'recent', businessId],
    queryFn: () => calls.recent(businessId!),
    enabled: Boolean(businessId),
    refetchInterval: 60_000,
  });

  const filtered = useMemo(() => {
    const all = recent.data ?? [];
    let list = all;
    if (filter === 'callbacks') list = list.filter((c) => c.callbackRequested);
    if (filter === 'hot') list = list.filter((c) => (c.interestRating ?? 0) >= 4);
    if (q.trim()) {
      const needle = q.toLowerCase();
      list = list.filter((c) =>
        [c.customerName, c.customerPhone, c.callSummary, c.queryType]
          .filter(Boolean)
          .some((v) => v!.toLowerCase().includes(needle)),
      );
    }
    return list.slice().sort(
      (a, b) => +new Date(b.callStartedAt) - +new Date(a.callStartedAt),
    );
  }, [recent.data, filter, q]);

  function selectFilter(next: FilterKey) {
    setFilter(next);
    if (next === 'all') {
      params.delete('filter');
    } else {
      params.set('filter', next);
    }
    setParams(params, { replace: true });
  }

  return (
    <>
      <PageHeader
        title="Calls"
        subtitle="Every conversation your AI receptionist has had."
      />
      <PageBody className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap gap-2">
            <FilterChip active={filter === 'all'} onClick={() => selectFilter('all')}>
              All
            </FilterChip>
            <FilterChip active={filter === 'callbacks'} onClick={() => selectFilter('callbacks')}>
              <PhoneForwarded className="h-3.5 w-3.5" /> Callbacks
            </FilterChip>
            <FilterChip active={filter === 'hot'} onClick={() => selectFilter('hot')}>
              <Star className="h-3.5 w-3.5" /> Hot leads
            </FilterChip>
          </div>
          <div className="relative w-full sm:w-72">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-9"
              placeholder="Search by name, phone, summary…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
        </div>

        <Card>
          <CardContent className="p-0">
            {recent.isLoading ? (
              <div className="p-4 space-y-3">
                {Array.from({ length: 8 }).map((_, i) => (
                  <Skeleton key={i} className="h-14" />
                ))}
              </div>
            ) : filtered.length === 0 ? (
              <EmptyState
                icon={<PhoneCall className="h-5 w-5" />}
                title={q || filter !== 'all' ? 'Nothing matches' : 'No calls yet'}
                description={
                  q || filter !== 'all'
                    ? 'Try adjusting your filter or search query.'
                    : "Once a customer calls your AI number, you'll see them here within seconds."
                }
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="border-b bg-muted/50 text-left">
                    <tr className="text-xs uppercase tracking-wide text-muted-foreground">
                      <th className="px-4 py-3 font-medium">Caller</th>
                      <th className="px-4 py-3 font-medium hidden md:table-cell">Summary</th>
                      <th className="px-4 py-3 font-medium">Time</th>
                      <th className="px-4 py-3 font-medium hidden sm:table-cell">Duration</th>
                      <th className="px-4 py-3 font-medium text-right">Signals</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map((c) => {
                      const fb = feedbackLabel(c.feedbackScore);
                      return (
                        <tr
                          key={c.id}
                          className="border-b transition-colors last:border-0 hover:bg-accent/30"
                        >
                          <td className="px-4 py-3">
                            <Link to={`/calls/${c.id}`} className="block min-w-0">
                              <div className="font-medium truncate max-w-[16rem]">
                                {c.customerName ?? c.customerPhone ?? 'Anonymous'}
                              </div>
                              {c.customerName && c.customerPhone && (
                                <div className="text-xs text-muted-foreground">
                                  {c.customerPhone}
                                </div>
                              )}
                            </Link>
                          </td>
                          <td className="px-4 py-3 hidden md:table-cell">
                            <Link to={`/calls/${c.id}`}>
                              <span className="line-clamp-1 text-muted-foreground max-w-md">
                                {c.callSummary ?? 'Summary pending…'}
                              </span>
                            </Link>
                          </td>
                          <td className="px-4 py-3 whitespace-nowrap text-muted-foreground">
                            {callRelative(c.callStartedAt)}
                          </td>
                          <td className="px-4 py-3 hidden sm:table-cell whitespace-nowrap text-muted-foreground">
                            {formatDuration(callDuration(c))}
                          </td>
                          <td className="px-4 py-3 text-right">
                            <div className="inline-flex flex-wrap justify-end gap-1.5">
                              {c.callbackRequested && (
                                <Badge variant="warning">Callback</Badge>
                              )}
                              {c.interestRating != null && (
                                <Badge variant={interestColor(c.interestRating) as never}>
                                  {c.interestRating} ★
                                </Badge>
                              )}
                              {fb && <Badge variant={fb.variant}>{fb.label}</Badge>}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </PageBody>
    </>
  );
}

function FilterChip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors ${
        active
          ? 'border-primary/40 bg-primary/10 text-primary'
          : 'border-border bg-background text-muted-foreground hover:bg-accent'
      }`}
    >
      {children}
    </button>
  );
}
