import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { subscription } from '@/api/resources';
import { useAuthStore } from '@/store/auth';
import { Button } from '@/components/ui/Button';
import { toast } from 'sonner';

function formatPrice(paise: number): string {
  return new Intl.NumberFormat('en-IN').format(paise / 100);
}

export function SubscriptionCard() {
  const { businessId } = useAuthStore();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: sub, isLoading, isError } = useQuery({
    queryKey: ['subscription', businessId],
    queryFn: () => subscription.current(businessId!),
    enabled: Boolean(businessId),
    retry: false,
  });

  const cancelMutation = useMutation({
    mutationFn: () => subscription.cancel(businessId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subscription', businessId] });
      toast.success('Subscription will be cancelled at the end of the billing period.');
    },
    onError: () => toast.error('Failed to cancel subscription'),
  });

  if (isLoading) {
    return (
      <div className="rounded-xl border bg-card p-6">
        <h3 className="text-lg font-semibold">Subscription</h3>
        <div className="mt-4 h-24 animate-pulse rounded bg-muted/20" />
      </div>
    );
  }

  if (isError || !sub) {
    return (
      <div className="rounded-xl border bg-card p-6">
        <h3 className="text-lg font-semibold">Subscription</h3>
        <p className="mt-3 text-sm text-muted-foreground">No active subscription.</p>
        <Button variant="outline" className="mt-4" onClick={() => navigate('/pricing')}>
          View Plans
        </Button>
      </div>
    );
  }

  const statusColors: Record<string, string> = {
    ACTIVE: 'bg-green-100 text-green-700',
    PENDING_SETUP: 'bg-yellow-100 text-yellow-700',
    PENDING_PAYMENT: 'bg-orange-100 text-orange-700',
    PAST_DUE: 'bg-red-100 text-red-700',
    CANCELLED: 'bg-gray-100 text-gray-600',
  };

  const usagePercent = sub.plan
    ? Math.min(100, Math.round((sub.callsUsed / sub.plan.callsIncluded) * 100))
    : 0;

  return (
    <div className="rounded-xl border bg-card p-6">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Subscription</h3>
        <span
          className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
            statusColors[sub.status] ?? 'bg-muted text-muted-foreground'
          }`}
        >
          {sub.status.replace(/_/g, ' ')}
        </span>
      </div>

      {sub.plan && (
        <div className="mt-4 space-y-3">
          <div className="flex items-baseline justify-between">
            <span className="text-xl font-semibold">{sub.plan.name}</span>
            <span className="text-sm text-muted-foreground">
              &#8377;{formatPrice(sub.plan.priceMonthly)}/mo
            </span>
          </div>

          <div>
            <div className="flex justify-between text-sm">
              <span>
                {sub.callsUsed}/{sub.plan.callsIncluded} calls used
              </span>
              <span className="text-muted-foreground">{sub.callsRemaining} remaining</span>
            </div>
            <div className="mt-1.5 h-2 w-full rounded-full bg-muted">
              <div
                className={`h-full rounded-full transition-all ${
                  usagePercent >= 90 ? 'bg-red-500' : usagePercent >= 70 ? 'bg-yellow-500' : 'bg-primary'
                }`}
                style={{ width: `${usagePercent}%` }}
              />
            </div>
          </div>

          {sub.daysRemaining > 0 && (
            <p className="text-xs text-muted-foreground">
              {sub.daysRemaining} days remaining in billing period
            </p>
          )}

          {sub.cancelAtPeriodEnd && (
            <p className="text-xs text-destructive">
              Cancelling at the end of current period
            </p>
          )}

          {sub.status === 'ACTIVE' && !sub.cancelAtPeriodEnd && (
            <Button
              variant="outline"
              size="sm"
              className="text-destructive hover:text-destructive"
              onClick={() => {
                if (confirm('Cancel subscription? Access continues until end of billing period.')) {
                  cancelMutation.mutate();
                }
              }}
              disabled={cancelMutation.isPending}
            >
              Cancel Subscription
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
