import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
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
} from 'lucide-react';
import { format } from 'date-fns';

import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { calls } from '@/api/resources';
import { useAuthStore } from '@/store/auth';
import {
  callDuration,
  interestColor,
  parseTranscript,
  type TranscriptTurn,
} from '@/features/calls/helpers';
import { formatDuration } from '@/lib/utils';

export default function CallDetailPage() {
  const { callId } = useParams<{ callId: string }>();
  const businessId = useAuthStore((s) => s.businessId);

  // The backend doesn't expose a per-call GET, so reuse the recent list and
  // pick the row by id. With more data this would be a dedicated endpoint.
  const q = useQuery({
    queryKey: ['calls', 'recent', businessId],
    queryFn: () => calls.recent(businessId!),
    enabled: Boolean(businessId),
  });
  const call = q.data?.find((c) => c.id === callId);

  return (
    <>
      <PageHeader
        title="Call detail"
        subtitle={call ? format(new Date(call.callStartedAt), 'EEEE, MMM d · HH:mm') : undefined}
        actions={
          <Link
            to="/calls"
            className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back
          </Link>
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
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="space-y-6 lg:col-span-2">
              <Card>
                <CardHeader>
                  <CardTitle>Summary</CardTitle>
                </CardHeader>
                <CardContent>
                  {call.callSummary ? (
                    <p className="text-sm leading-6 whitespace-pre-wrap">{call.callSummary}</p>
                  ) : (
                    <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
                      Summary is still being generated. Refresh in a few seconds.
                    </div>
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
              <Card>
                <CardHeader>
                  <CardTitle>Caller</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <Row label="Name" value={call.customerName ?? '—'} />
                  <Row
                    label="Phone"
                    value={call.customerPhone ?? '—'}
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
                      call.interestRating != null ? (
                        <Badge variant={interestColor(call.interestRating) as never}>
                          {call.interestRating} ★
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
                      call.callbackRequested ? (
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
            </div>
          </div>
        )}
      </PageBody>
    </>
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
          isUser ? 'bg-secondary text-secondary-foreground' : 'bg-primary/10 text-primary'
        }`}
      >
        {isUser ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
      </div>
      <div
        className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-6 ${
          isUser
            ? 'bg-secondary text-secondary-foreground rounded-tr-sm'
            : 'bg-primary/10 text-foreground rounded-tl-sm'
        }`}
      >
        {text}
      </div>
    </li>
  );
}
