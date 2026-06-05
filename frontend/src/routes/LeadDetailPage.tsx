import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  ArrowLeft,
  CalendarCheck,
  Check,
  Clock,
  MessageSquare,
  Phone,
  PhoneCall,
  Star,
  X,
  ChevronsRight,
} from 'lucide-react';
import { format } from 'date-fns';

import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
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

export default function LeadDetailPage() {
  const { leadId } = useParams<{ leadId: string }>();
  const businessId = useAuthStore((s) => s.businessId)!;
  const navigate = useNavigate();
  const qc = useQueryClient();

  const q = useQuery({
    queryKey: ['leads', businessId, leadId],
    queryFn: () => leads.get(businessId, leadId!),
    enabled: Boolean(businessId && leadId),
  });

  const onActioned = (_newStatus: 'APPROVED' | 'DECLINED' | 'IGNORED') => {
    qc.setQueryData<import('@/types/api').LeadResponse[]>(
      ['leads', businessId],
      (old) => old?.filter((l) => l.id !== leadId),
    );
    qc.invalidateQueries({ queryKey: ['leads', businessId] });
    qc.invalidateQueries({ queryKey: ['leads', businessId, leadId] });
    qc.invalidateQueries({ queryKey: ['leads', businessId, 'pending-count'] });
  };

  const lead = q.data;
  const isAppointment = lead?.leadType === 'APPOINTMENT';
  const isTerminal = lead && lead.status !== 'NEW';

  // datetime-local input (no timezone). Pre-fill from suggestedDatetime.
  const [slot, setSlot] = useState('');
  const [reason, setReason] = useState('');
  useEffect(() => {
    if (lead?.suggestedDatetime) {
      setSlot(toLocalInputValue(lead.suggestedDatetime));
    } else {
      setSlot('');
    }
  }, [lead?.suggestedDatetime]);

  const approve = useMutation({
    mutationFn: () =>
      leads.approve(businessId, leadId!, isAppointment ? toIso(slot) : null),
    onSuccess: () => {
      toast.success(
        isAppointment ? 'Appointment confirmed. WhatsApp sent.' : 'Lead approved. Customer notified.',
      );
      onActioned('APPROVED');
      navigate('/leads');
    },
    onError: () => {/* axios interceptor toasts the error */},
  });

  const decline = useMutation({
    mutationFn: () => leads.decline(businessId, leadId!, reason.trim()),
    onSuccess: () => {
      toast.success('Lead declined. Customer notified with your reason.');
      onActioned('DECLINED');
      navigate('/leads');
    },
  });

  const ignore = useMutation({
    mutationFn: () => leads.ignore(businessId, leadId!),
    onSuccess: () => {
      toast.success("Dismissed. Customer wasn't notified.");
      onActioned('IGNORED');
      navigate('/leads');
    },
  });

  return (
    <>
      <PageHeader
        title="Lead detail"
        subtitle={lead ? `Captured ${leadAge(lead)}` : undefined}
        actions={
          <Link
            to="/leads"
            className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> All leads
          </Link>
        }
      />
      <PageBody>
        {q.isLoading ? (
          <div className="space-y-4">
            <Skeleton className="h-40" />
            <Skeleton className="h-64" />
          </div>
        ) : !lead ? (
          <EmptyState
            icon={<PhoneCall className="h-5 w-5" />}
            title="Lead not found"
            description="It may have been removed, or you may not have access."
          />
        ) : (
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="space-y-6 lg:col-span-2">
              <Card>
                <CardHeader>
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={leadTypeVariant(lead.leadType) as never}>
                      {leadTypeLabel(lead.leadType)}
                    </Badge>
                    <Badge variant={leadStatusVariant(lead.status) as never}>
                      {leadStatusLabel(lead.status)}
                    </Badge>
                    {lead.interestRating != null && (
                      <span className="inline-flex items-center gap-1 text-sm text-muted-foreground">
                        <Star className="h-4 w-4 text-[hsl(var(--warning))]" />
                        {formatInterest(lead.interestRating)} / 10
                      </span>
                    )}
                  </div>
                  <CardTitle className="mt-2">
                    {lead.customerName ?? lead.customerPhone ?? 'Anonymous caller'}
                  </CardTitle>
                  {lead.summary && (
                    <CardDescription className="mt-1 leading-6">
                      {lead.summary}
                    </CardDescription>
                  )}
                </CardHeader>
                {isAppointment && (lead.preferredWindowRaw || lead.service) && (
                  <CardContent className="space-y-3 border-t pt-4">
                    {lead.service && <DetailRow label="Service" value={lead.service} />}
                    {lead.preferredWindowRaw && (
                      <DetailRow label="Preferred window" value={lead.preferredWindowRaw} />
                    )}
                    {lead.suggestedDatetime && (
                      <DetailRow
                        label="AI suggested"
                        value={format(new Date(lead.suggestedDatetime), 'EEE, MMM d · h:mm a')}
                      />
                    )}
                  </CardContent>
                )}
              </Card>

              {!isTerminal ? (
                <Card>
                  <CardHeader>
                    <CardTitle>Take action</CardTitle>
                    <CardDescription>
                      {isAppointment
                        ? 'Confirm with the slot you want to commit to, or decline with a reason.'
                        : 'Approve to tell the caller a human will follow up. Dismiss to drop silently.'}
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-5">
                    {isAppointment && (
                      <div className="space-y-1.5">
                        <Label>Confirmed date & time</Label>
                        <Input
                          type="datetime-local"
                          value={slot}
                          onChange={(e) => setSlot(e.target.value)}
                        />
                        <p className="text-xs text-muted-foreground">
                          {lead.suggestedDatetime
                            ? "Pre-filled with the AI's best guess — change if needed."
                            : "Caller didn't specify a time. Pick what works for you."}
                        </p>
                      </div>
                    )}

                    <div className="flex flex-wrap gap-2">
                      <Button
                        onClick={() => approve.mutate()}
                        loading={approve.isPending}
                        disabled={isAppointment && !slot}
                      >
                        <Check className="h-4 w-4" />
                        {isAppointment ? 'Confirm appointment' : 'Approve & notify'}
                      </Button>

                      {lead.customerPhone && (
                        <Button
                          type="button"
                          variant="outline"
                          onClick={() => copyPhone(lead.customerPhone!)}
                        >
                          <Phone className="h-4 w-4" /> Copy number
                        </Button>
                      )}

                      <Button
                        variant="ghost"
                        onClick={() => ignore.mutate()}
                        loading={ignore.isPending}
                      >
                        <ChevronsRight className="h-4 w-4" /> Do nothing
                      </Button>
                    </div>

                    {isAppointment && (
                      <details className="rounded-lg border bg-muted/30 p-4">
                        <summary className="cursor-pointer text-sm font-medium text-destructive">
                          Decline with reason
                        </summary>
                        <div className="mt-3 space-y-2">
                          <Label>Reason (sent to the customer on WhatsApp)</Label>
                          <Textarea
                            rows={3}
                            placeholder="e.g. We're fully booked that week."
                            value={reason}
                            onChange={(e) => setReason(e.target.value)}
                          />
                          <div className="flex justify-end">
                            <Button
                              variant="destructive"
                              size="sm"
                              loading={decline.isPending}
                              disabled={!reason.trim()}
                              onClick={() => decline.mutate()}
                            >
                              <X className="h-4 w-4" /> Decline
                            </Button>
                          </div>
                        </div>
                      </details>
                    )}
                  </CardContent>
                </Card>
              ) : (
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      {lead.status === 'APPROVED' && <CalendarCheck className="h-4 w-4 text-[hsl(var(--success))]" />}
                      {lead.status === 'DECLINED' && <X className="h-4 w-4 text-destructive" />}
                      {lead.status === 'IGNORED' && <ChevronsRight className="h-4 w-4 text-muted-foreground" />}
                      Already {leadStatusLabel(lead.status).toLowerCase()}
                    </CardTitle>
                    {lead.decidedAt && (
                      <CardDescription>
                        {format(new Date(lead.decidedAt), 'EEE, MMM d · h:mm a')}
                        {lead.decidedVia && ` · via ${lead.decidedVia.toLowerCase()}`}
                      </CardDescription>
                    )}
                  </CardHeader>
                  <CardContent className="space-y-2 text-sm">
                    {lead.confirmedDatetime && (
                      <DetailRow
                        label="Confirmed slot"
                        value={format(new Date(lead.confirmedDatetime), 'EEE, MMM d · h:mm a')}
                      />
                    )}
                    {lead.declineReason && (
                      <DetailRow label="Decline reason" value={lead.declineReason} />
                    )}
                  </CardContent>
                </Card>
              )}
            </div>

            <div className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>Caller</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <DetailRow label="Name" value={lead.customerName ?? '—'} />
                  <DetailRow
                    label="Phone"
                    value={lead.customerPhone ?? '—'}
                    icon={<Phone className="h-3.5 w-3.5" />}
                    valueClassName="font-mono text-sm font-medium text-right"
                  />
                  <DetailRow label="Language" value={lead.callerLanguage ?? '—'} />
                  <DetailRow
                    label="Captured"
                    value={leadAge(lead)}
                    icon={<Clock className="h-3.5 w-3.5" />}
                  />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Notifications</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <DetailRow
                    label="Reminders sent"
                    value={String(lead.remindersSent)}
                    icon={<MessageSquare className="h-3.5 w-3.5" />}
                  />
                  <DetailRow
                    label="Owner notified"
                    value={lead.ownerNotifiedAt ? '✓' : 'Queued'}
                  />
                  <DetailRow
                    label="Customer notified"
                    value={
                      lead.customerNotifiedAt ? '✓'
                        : lead.status === 'IGNORED' ? 'n/a'
                        : 'Queued'
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

function DetailRow({
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
      <span className="text-muted-foreground inline-flex items-center gap-1.5">
        {icon}
        {label}
      </span>
      <span className={valueClassName ?? 'font-medium text-right break-words'}>{value}</span>
    </div>
  );
}

/** Copy the caller's number to clipboard + flash a toast. Works on every
 *  device (dashboard is most often opened on a laptop where tel: links are
 *  unreliable, so we don't bother with auto-dial). The owner copies, then
 *  dials from whatever device / app they prefer. */
async function copyPhone(phone: string) {
  try {
    await navigator.clipboard.writeText(phone);
    toast.success('Phone number copied', { description: phone });
  } catch {
    // Clipboard API can fail on insecure origins / older browsers.
    // Fallback: still tell them the number so they can copy manually.
    toast.message('Copy unavailable — number:', { description: phone });
  }
}

// datetime-local <input> wants 'YYYY-MM-DDTHH:mm' in LOCAL time.
function toLocalInputValue(isoUtc: string): string {
  const d = new Date(isoUtc);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
function toIso(localValue: string): string | null {
  if (!localValue) return null;
  return new Date(localValue).toISOString();
}
