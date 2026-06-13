import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  Phone,
  Clock,
  PhoneForwarded,
  Star,
  ThumbsUp,
  ThumbsDown,
  FileText,
  Bot,
  User,
  HelpCircle,
  AlertCircle,
  Trash2,
  Brain,
} from 'lucide-react';
import { motion } from 'framer-motion';
import { format } from 'date-fns';

import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { calls } from '@/api/resources';
import { isAdmin, useAuthStore } from '@/store/auth';
import {
  callDuration,
  interestColor,
  parseTranscript,
  type TranscriptTurn,
} from '@/features/calls/helpers';
import { formatDuration } from '@/lib/utils';
import { useSummariesByCallId } from '@/features/calls/useSummaries';

export default function CallDetailPage() {
  const { callId } = useParams<{ callId: string }>();
  const businessId = useAuthStore((s) => s.businessId);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [confirmDelete, setConfirmDelete] = useState(false);

  // The backend doesn't expose a per-call GET, so reuse the recent list and
  // pick the row by id. With more data this would be a dedicated endpoint.
  const q = useQuery({
    queryKey: ['calls', 'recent', businessId],
    queryFn: () => calls.recent(businessId!),
    enabled: Boolean(businessId),
  });
  const call = q.data?.find((c) => c.id === callId);

  const deleteMutation = useMutation({
    mutationFn: () => calls.delete(businessId!, callId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calls'] });
      navigate('/calls');
    },
  });

  const { map: summaryMap } = useSummariesByCallId();
  const summary = call ? summaryMap.get(call.id) : undefined;

  // Prefer fields from summary-service over the call-orch row — the summary
  // table is the source of truth for AI-derived analytics. call_logs only
  // carries the synchronous bits we knew during the call.
  const interestRating = summary?.interestRating ?? call?.interestRating ?? null;
  const callbackNeeded = summary?.callbackNeeded ?? call?.callbackRequested ?? false;
  const summaryText = summary?.summaryText ?? call?.callSummary ?? null;

  return (
    <>
      <PageHeader
        title="Call detail"
        subtitle={call ? format(new Date(call.callStartedAt), 'EEEE, MMM d · HH:mm') : undefined}
        actions={
          <div className="flex items-center gap-3">
            {call && isAdmin() && (
              confirmDelete ? (
                <div className="flex items-center gap-2">
                  <span className="text-sm text-destructive">Delete this call?</span>
                  <button
                    onClick={() => deleteMutation.mutate()}
                    disabled={deleteMutation.isPending}
                    className="inline-flex items-center gap-1 rounded-md bg-destructive px-3 py-1.5 text-sm font-medium text-destructive-foreground hover:bg-destructive/90 disabled:opacity-50"
                  >
                    {deleteMutation.isPending ? 'Deleting…' : 'Yes, delete'}
                  </button>
                  <button
                    onClick={() => setConfirmDelete(false)}
                    className="text-sm font-medium text-muted-foreground hover:text-foreground"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => setConfirmDelete(true)}
                  className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-destructive"
                >
                  <Trash2 className="h-4 w-4" /> Delete
                </button>
              )
            )}
            <Link
              to="/calls"
              className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground"
            >
              <ArrowLeft className="h-4 w-4" /> Back
            </Link>
          </div>
        }
      />
      <PageBody>
        {q.isLoading ? (
          <div className="space-y-4">
            <Skeleton className="h-32" />
            <Skeleton className="h-80" />
          </div>
        ) : !call ? (
          <EmptyState
            icon={<Phone className="h-5 w-5" />}
            title="Call not found"
            description="This call may have been removed, or you may not have access to it."
          />
        ) : (
          <motion.div
            className="grid gap-6 lg:grid-cols-3"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: 'easeOut' }}
          >
            <div className="space-y-6 lg:col-span-2">
              <Card>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="flex items-center gap-2">
                        <Brain className="h-4 w-4 text-primary" />
                        <span className="gradient-text">AI summary</span>
                      </CardTitle>
                      {summary?.queryType && (
                        <CardDescription className="mt-1 capitalize">
                          {summary.queryType.replace(/[_-]/g, ' ').toLowerCase()}
                        </CardDescription>
                      )}
                    </div>
                    {summary && (
                      <Badge variant="outline">Generated</Badge>
                    )}
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  {summaryText ? (
                    <p className="border-l-2 border-primary/30 pl-3 text-sm leading-6 whitespace-pre-wrap">{summaryText}</p>
                  ) : (
                    <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
                      Summary is still being generated. It usually lands a few seconds after the call ends.
                    </div>
                  )}

                  {summary?.mainConcerns && summary.mainConcerns.length > 0 && (
                    <Section icon={<AlertCircle className="h-4 w-4 text-[hsl(var(--warning))]" />} label="Main concerns">
                      <ul className="grid gap-1.5 sm:grid-cols-2">
                        {summary.mainConcerns.map((c, i) => (
                          <li key={i} className="rounded-md bg-accent/40 px-3 py-2 text-sm leading-5">
                            {c}
                          </li>
                        ))}
                      </ul>
                    </Section>
                  )}

                  {summary?.unansweredQuestions && summary.unansweredQuestions.length > 0 && (
                    <Section icon={<HelpCircle className="h-4 w-4 text-destructive" />} label="Questions the AI couldn't answer">
                      <ul className="space-y-1.5">
                        {summary.unansweredQuestions.map((c, i) => (
                          <li
                            key={i}
                            className="rounded-md border border-destructive/20 bg-destructive/5 px-3 py-2 text-sm leading-5"
                          >
                            {c}
                          </li>
                        ))}
                      </ul>
                    </Section>
                  )}

                  {summary?.callbackNeeded && summary?.callbackReason && (
                    <Section icon={<PhoneForwarded className="h-4 w-4 text-[hsl(var(--warning))]" />} label="Callback reason">
                      <p className="rounded-md border border-[hsl(var(--warning))]/30 bg-[hsl(var(--warning))]/5 px-3 py-2 text-sm leading-5">
                        {summary.callbackReason}
                      </p>
                    </Section>
                  )}

                  {summary?.interestReason && (
                    <Section icon={<Star className="h-4 w-4 text-primary" />} label="Why this interest score">
                      <p className="text-sm text-muted-foreground italic">"{summary.interestReason}"</p>
                    </Section>
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <FileText className="h-4 w-4" />
                    Transcript
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <TranscriptView raw={call.transcript} />
                </CardContent>
              </Card>
            </div>

            <div className="space-y-4">
              <motion.div
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, ease: 'easeOut', delay: 0.1 }}
              >
                <Card>
                  <CardHeader>
                    <CardTitle>Caller</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3 text-sm">
                    <Row label="Name" value={summary?.callerName ?? call.customerName ?? '—'} />
                    <Row
                      label="Phone"
                      value={summary?.customerPhone ?? call.customerPhone ?? '—'}
                      icon={<Phone className="h-3.5 w-3.5" />}
                    />
                    <Row label="Provider" value={call.provider} />
                    <Row
                      label="Call ID"
                      value={call.providerCallId}
                      valueClassName="font-mono text-xs"
                    />
                  </CardContent>
                </Card>
              </motion.div>

              <motion.div
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, ease: 'easeOut', delay: 0.2 }}
              >
                <Card>
                  <CardHeader>
                    <CardTitle>Outcome</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3 text-sm">
                    <Row
                      label="Duration"
                      value={formatDuration(callDuration(call))}
                      icon={<Clock className="h-3.5 w-3.5" />}
                    />
                    <Row
                      label="Interest"
                      value={
                        interestRating != null ? (
                          <Badge variant={interestColor(interestRating) as never}>
                            {interestRating}/10 ★
                          </Badge>
                        ) : (
                          '—'
                        )
                      }
                      icon={<Star className="h-3.5 w-3.5" />}
                    />
                    <Row
                      label="Feedback"
                      value={
                        call.feedbackScore == null ? (
                          '—'
                        ) : call.feedbackScore === 1 ? (
                          <Badge variant="success">
                            <ThumbsUp className="mr-1 h-3 w-3" /> Helpful
                          </Badge>
                        ) : (
                          <Badge variant="destructive">
                            <ThumbsDown className="mr-1 h-3 w-3" /> Not helpful
                          </Badge>
                        )
                      }
                    />
                    <Row
                      label="Callback"
                      value={
                        callbackNeeded ? (
                          <Badge variant="warning">
                            <PhoneForwarded className="mr-1 h-3 w-3" /> Pending
                          </Badge>
                        ) : (
                          'Not requested'
                        )
                      }
                    />
                  </CardContent>
                </Card>
              </motion.div>
            </div>
          </motion.div>
        )}
      </PageBody>
    </>
  );
}

function Section({
  icon,
  label,
  children,
}: {
  icon: React.ReactNode;
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-2 border-t border-border/40 pt-4">
      <div className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {icon} {label}
      </div>
      {children}
    </div>
  );
}

function Row({
  label,
  value,
  icon,
  valueClassName,
}: {
  label: string;
  value: React.ReactNode;
  icon?: React.ReactNode;
  valueClassName?: string;
}) {
  return (
    <div className="flex items-start justify-between gap-3">
      <span className="text-muted-foreground flex items-center gap-1.5">
        {icon}
        {label}
      </span>
      <span className={valueClassName ?? 'font-medium text-right break-all'}>{value}</span>
    </div>
  );
}

function TranscriptView({ raw }: { raw: string | null }) {
  const turns = parseTranscript(raw);
  if (!turns || turns.length === 0) {
    return (
      <EmptyState
        icon={<FileText className="h-5 w-5" />}
        title="No transcript"
        description="This call ended too quickly to produce a transcript, or it's still being processed."
        className="py-10"
      />
    );
  }
  return (
    <ul className="space-y-3">
      {turns.map((t, idx) => (
        <TurnRow key={idx} turn={t} />
      ))}
    </ul>
  );
}

function TurnRow({ turn }: { turn: TranscriptTurn }) {
  const speaker = (turn.speaker ?? turn.role ?? '').toLowerCase();
  const isUser = speaker.includes('user') || speaker.includes('customer');
  const text = turn.text ?? turn.content ?? '';
  return (
    <li className={`flex gap-3 ${isUser ? 'flex-row-reverse' : ''}`}>
      <div
        className={`grid h-8 w-8 shrink-0 place-items-center rounded-full text-xs font-semibold ${
          isUser ? 'bg-secondary text-secondary-foreground' : 'bg-primary/10 text-primary border border-primary/20'
        }`}
      >
        {isUser ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
      </div>
      <div
        className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-6 ${
          isUser
            ? 'bg-secondary text-secondary-foreground rounded-tr-sm'
            : 'bg-primary/10 text-foreground rounded-tl-sm border border-primary/20'
        }`}
      >
        {text}
      </div>
    </li>
  );
}
