import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Inbox, Phone, Star, ArrowRight, Clock } from 'lucide-react';
import { motion } from 'framer-motion';

import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { Card, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/Tabs';
import { leads } from '@/api/resources';
import { useAuthStore } from '@/store/auth';
import {
  formatInterest,
  leadAge,
  leadStatusLabel,
  leadStatusVariant,
  leadTypeLabel,
  leadTypeVariant,
} from '@/features/leads/helpers';
import type { LeadResponse } from '@/types/api';

export default function LeadsListPage() {
  const businessId = useAuthStore((s) => s.businessId)!;
  const [tab, setTab] = useState<'pending' | 'handled'>('pending');

  const q = useQuery({
    queryKey: ['leads', businessId],
    queryFn: () => leads.list(businessId),
    refetchInterval: 30_000,
  });

  const { pending, handled } = useMemo(() => {
    const all = q.data ?? [];
    const p: LeadResponse[] = [];
    const h: LeadResponse[] = [];
    for (const l of all) (l.status === 'NEW' ? p : h).push(l);
    // Pending: oldest-first so things don't fall through the cracks.
    p.sort((a, b) => +new Date(a.createdAt) - +new Date(b.createdAt));
    h.sort((a, b) => +new Date(b.decidedAt ?? b.createdAt) - +new Date(a.decidedAt ?? a.createdAt));
    return { pending: p, handled: h };
  }, [q.data]);

  return (
    <>
      <PageHeader
        title="Leads"
        subtitle="Calls that need your attention. Approve, decline, or dismiss."
      />
      <PageBody>
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut', delay: 0.05 }}
        >
          <Tabs value={tab} onValueChange={(v) => setTab(v as 'pending' | 'handled')}>
            <TabsList>
              <TabsTrigger value="pending">
                Pending
                {pending.length > 0 && (
                  <span className="ml-2 inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-[10px] font-semibold text-primary-foreground">
                    {pending.length}
                  </span>
                )}
              </TabsTrigger>
              <TabsTrigger value="handled">Handled</TabsTrigger>
            </TabsList>
            <TabsContent value="pending">
              <LeadList rows={pending} loading={q.isLoading} emptyMessage="No pending leads." />
            </TabsContent>
            <TabsContent value="handled">
              <LeadList rows={handled} loading={q.isLoading} emptyMessage="Nothing handled yet." />
            </TabsContent>
          </Tabs>
        </motion.div>
      </PageBody>
    </>
  );
}

function LeadList({
  rows,
  loading,
  emptyMessage,
}: {
  rows: LeadResponse[];
  loading: boolean;
  emptyMessage: string;
}) {
  if (loading) {
    return (
      <Card>
        <CardContent className="p-4 space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </CardContent>
      </Card>
    );
  }
  if (rows.length === 0) {
    return (
      <Card>
        <CardContent className="p-0">
          <EmptyState
            icon={<Inbox className="h-5 w-5" />}
            title={emptyMessage}
            description="Leads land here when a caller wants an appointment, scores high interest, or asks for a human."
          />
        </CardContent>
      </Card>
    );
  }
  return (
    <Card>
      <CardContent className="p-0">
        <ul className="divide-y divide-border/40">
          {rows.map((l, index) => (
            <motion.li
              key={l.id}
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.06, duration: 0.3 }}
            >
              <Link
                to={`/leads/${l.id}`}
                className="grid grid-cols-[1fr_auto] items-start gap-4 p-4 transition-all duration-150 hover:bg-primary/[0.04] hover:translate-x-0.5"
              >
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="truncate font-medium">
                      {l.customerName ?? l.customerPhone ?? 'Anonymous caller'}
                    </p>
                    <Badge variant={leadTypeVariant(l.leadType) as never}>
                      {leadTypeLabel(l.leadType)}
                    </Badge>
                    {l.status !== 'NEW' && (
                      <Badge variant={leadStatusVariant(l.status) as never}>
                        {leadStatusLabel(l.status)}
                      </Badge>
                    )}
                    {l.interestRating != null && (
                      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                        <Star className="h-3.5 w-3.5 text-[hsl(var(--warning))]" />
                        {formatInterest(l.interestRating)}/10
                      </span>
                    )}
                  </div>
                  {l.summary && (
                    <p className="mt-1.5 line-clamp-2 text-sm text-muted-foreground">{l.summary}</p>
                  )}
                  <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                    <span className="inline-flex items-center gap-1">
                      <Clock className="h-3.5 w-3.5" /> {leadAge(l)}
                    </span>
                    {l.customerPhone && (
                      <span className="inline-flex items-center gap-1 font-mono">
                        <Phone className="h-3.5 w-3.5" /> {l.customerPhone}
                      </span>
                    )}
                    {l.status === 'NEW' && l.remindersSent > 0 && (
                      <Badge variant="outline">{l.remindersSent} reminders sent</Badge>
                    )}
                  </div>
                </div>
                <ArrowRight className="mt-1 h-4 w-4 text-muted-foreground shrink-0" />
              </Link>
            </motion.li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
