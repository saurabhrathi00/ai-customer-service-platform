import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PhoneCall, Search, PhoneForwarded, Star, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { motion } from 'framer-motion';

import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { Card, CardContent } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Skeleton } from '@/components/ui/Skeleton';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { calls } from '@/api/resources';
import { useAuthStore } from '@/store/auth';
import { callDuration, callRelative, feedbackLabel, interestColor } from '@/features/calls/helpers';
import { useSummariesByCallId } from '@/features/calls/useSummaries';
import { formatDuration } from '@/lib/utils';

type FilterKey = 'all' | 'callbacks' | 'hot';

export default function CallsListPage() {
  const [params, setParams] = useSearchParams();
  const businessId = useAuthStore((s) => s.businessId);
  const filterParam = (params.get('filter') as FilterKey) || 'all';
  const [filter, setFilter] = useState<FilterKey>(filterParam);
  const [q, setQ] = useState('');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const qc = useQueryClient();

  const recent = useQuery({
    queryKey: ['calls', 'recent', businessId],
    queryFn: () => calls.recent(businessId!),
    enabled: Boolean(businessId),
    refetchInterval: 60_000,
  });
  const { map: summaryMap } = useSummariesByCallId();

  const filtered = useMemo(() => {
    const all = recent.data ?? [];
    const eff = (c: typeof all[number]) => {
      const s = summaryMap.get(c.id);
      return {
        interest: s?.interestRating ?? c.interestRating ?? null,
        callback: s?.callbackNeeded ?? c.callbackRequested,
      };
    };
    let list = all;
    if (filter === 'callbacks') list = list.filter((c) => eff(c).callback);
    if (filter === 'hot') list = list.filter((c) => (eff(c).interest ?? 0) >= 4);
    if (q.trim()) {
      const needle = q.toLowerCase();
      list = list.filter((c) =>
        [c.customerName, c.customerPhone, summaryMap.get(c.id)?.summaryText ?? c.callSummary, c.queryType]
          .filter(Boolean)
          .some((v) => v!.toLowerCase().includes(needle)),
      );
    }
    return list.slice().sort((a, b) => {
      if (filter === 'callbacks') {
        const ia = eff(a).interest ?? -1;
        const ib = eff(b).interest ?? -1;
        if (ib !== ia) return ib - ia;
      }
      return +new Date(b.callStartedAt) - +new Date(a.callStartedAt);
    });
  }, [recent.data, filter, q, summaryMap]);

  const bulkDelete = useMutation({
    mutationFn: () => calls.deleteBulk(businessId!, [...selected]),
    onSuccess: (data) => {
      toast.success(`${data.deleted} call${data.deleted === 1 ? '' : 's'} deleted`);
      setSelected(new Set());
      qc.invalidateQueries({ queryKey: ['calls', 'recent', businessId] });
    },
    onError: () => toast.error('Failed to delete calls'),
  });

  function toggleSelect(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleAll() {
    if (selected.size === filtered.length) {
      setSelected(new Set());
    } else {
      setSelected(new Set(filtered.map((c) => c.id)));
    }
  }

  function selectFilter(next: FilterKey) {
    setFilter(next);
    setSelected(new Set());
    if (next === 'all') {
      params.delete('filter');
    } else {
      params.set('filter', next);
    }
    setParams(params, { replace: true });
  }

  const allChecked = filtered.length > 0 && selected.size === filtered.length;
  const someChecked = selected.size > 0 && selected.size < filtered.length;

  return (
    <>
      <PageHeader
        title="Calls"
        subtitle="Every conversation your AI voice agent has had."
      />
      <PageBody className="space-y-4">
        <motion.div
          className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut', delay: 0.05 }}
        >
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
          <div className="flex items-center gap-2">
            {selected.size > 0 && (
              <Button
                variant="destructive"
                size="sm"
                onClick={() => bulkDelete.mutate()}
                loading={bulkDelete.isPending}
              >
                <Trash2 className="h-3.5 w-3.5" />
                Delete {selected.size}
              </Button>
            )}
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
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut', delay: 0.15 }}
        >
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
                      <th className="px-4 py-3 w-10">
                        <input
                          type="checkbox"
                          checked={allChecked}
                          ref={(el) => { if (el) el.indeterminate = someChecked; }}
                          onChange={toggleAll}
                          className="h-4 w-4 rounded border-border"
                        />
                      </th>
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
                      const s = summaryMap.get(c.id);
                      const summaryText = s?.summaryText ?? c.callSummary;
                      const interest = s?.interestRating ?? c.interestRating;
                      const callback = s?.callbackNeeded ?? c.callbackRequested;
                      const checked = selected.has(c.id);
                      return (
                        <tr
                          key={c.id}
                          className={`border-b border-border/40 transition-colors duration-150 last:border-0 cursor-pointer hover:bg-primary/[0.04] ${
                            checked ? 'bg-primary/[0.05]' : ''
                          }`}
                        >
                          <td className="px-4 py-3">
                            <input
                              type="checkbox"
                              checked={checked}
                              onChange={() => toggleSelect(c.id)}
                              className="h-4 w-4 rounded border-border"
                            />
                          </td>
                          <td className="px-4 py-3">
                            <Link to={`/calls/${c.id}`} className="block min-w-0">
                              <div className="font-medium truncate max-w-[16rem]">
                                {s?.callerName ?? c.customerName ?? c.customerPhone ?? 'Anonymous'}
                              </div>
                              {(s?.customerPhone ?? c.customerPhone) && (s?.callerName ?? c.customerName) && (
                                <div className="text-xs text-muted-foreground">
                                  {s?.customerPhone ?? c.customerPhone}
                                </div>
                              )}
                            </Link>
                          </td>
                          <td className="px-4 py-3 hidden md:table-cell">
                            <Link to={`/calls/${c.id}`}>
                              <span className="line-clamp-1 text-muted-foreground max-w-md">
                                {summaryText ?? 'Summary pending…'}
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
                              {callback && <Badge variant="warning">Callback</Badge>}
                              {interest != null && (
                                <Badge variant={interestColor(interest) as never}>
                                  {interest}/10 ★
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
        </motion.div>
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
          : 'border-border/40 bg-background text-muted-foreground hover:bg-primary/5'
      }`}
    >
      {children}
    </button>
  );
}
