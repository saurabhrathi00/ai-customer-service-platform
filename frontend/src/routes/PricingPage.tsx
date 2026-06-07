import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Check, X } from 'lucide-react';
import { subscription } from '@/api/resources';
import { Button } from '@/components/ui/Button';
import type { PlanResponse } from '@/types/api';

function formatPrice(paise: number): string {
  return new Intl.NumberFormat('en-IN').format(paise / 100);
}

const FEATURE_LABELS: Record<string, string> = {
  post_call_summary: 'Post-call Summary',
  crm_integration: 'CRM Integration',
  custom_voice: 'Custom Voice',
  availability: '24/7 Availability',
};

function PlanCard({ plan }: { plan: PlanResponse }) {
  const navigate = useNavigate();
  const features = plan.features as Record<string, unknown>;
  const languages = (features.languages as string[]) ?? ['Hindi', 'English'];

  return (
    <div
      className={`relative flex flex-col rounded-xl border p-6 ${
        plan.isPopular ? 'border-primary ring-2 ring-primary/20' : ''
      }`}
    >
      {plan.isPopular && (
        <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-primary px-3 py-0.5 text-xs font-semibold text-primary-foreground">
          Most Popular
        </span>
      )}

      <h3 className="text-xl font-semibold">{plan.name}</h3>
      {plan.description && (
        <p className="mt-1 text-sm text-muted-foreground">{plan.description}</p>
      )}

      <div className="mt-4">
        <span className="text-3xl font-bold">&#8377;{formatPrice(plan.priceMonthly)}</span>
        <span className="text-muted-foreground">/month</span>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">+ 18% GST</p>

      <ul className="mt-6 flex-1 space-y-3 text-sm">
        <FeatureLine ok>{plan.callsIncluded} calls/month</FeatureLine>
        <FeatureLine ok>
          Max {Math.floor(plan.maxCallDurationSec / 60)} min per call
        </FeatureLine>
        <FeatureLine ok>
          {plan.channels} concurrent channel{plan.channels > 1 ? 's' : ''}
        </FeatureLine>
        <FeatureLine ok>
          {plan.phoneNumbers} phone number{plan.phoneNumbers > 1 ? 's' : ''}
        </FeatureLine>
        <FeatureLine ok>{languages.join(' + ')}</FeatureLine>
        <FeatureLine ok={features.post_call_summary as boolean}>
          {FEATURE_LABELS.post_call_summary}
        </FeatureLine>
        <FeatureLine ok={features.crm_integration as boolean}>
          {FEATURE_LABELS.crm_integration}
        </FeatureLine>
        <FeatureLine ok={features.custom_voice as boolean}>
          {FEATURE_LABELS.custom_voice}
        </FeatureLine>
      </ul>

      <p className="mt-4 text-xs text-muted-foreground">
        Extra calls: &#8377;{formatPrice(plan.extraCallRate)}/call
      </p>

      <Button
        className="mt-6 w-full"
        size="lg"
        variant={plan.isPopular ? 'default' : 'outline'}
        onClick={() => navigate(`/checkout/${plan.slug}`)}
      >
        Get Started
      </Button>
    </div>
  );
}

function FeatureLine({ ok, children }: { ok?: boolean; children: React.ReactNode }) {
  return (
    <li className="flex items-center gap-2">
      {ok ? (
        <Check className="h-4 w-4 shrink-0 text-green-500" />
      ) : (
        <X className="h-4 w-4 shrink-0 text-muted-foreground/40" />
      )}
      <span className={ok ? '' : 'text-muted-foreground/60'}>{children}</span>
    </li>
  );
}

export default function PricingPage() {
  const { data: plans, isLoading } = useQuery({
    queryKey: ['plans'],
    queryFn: subscription.plans,
    staleTime: 5 * 60_000,
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
      <div className="text-center">
        <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">
          Simple, transparent pricing
        </h1>
        <p className="mx-auto mt-4 max-w-2xl text-lg text-muted-foreground">
          Your AI receptionist works 24/7, never takes a day off, and costs a fraction of a
          human receptionist. Cancel anytime.
        </p>
      </div>

      {isLoading ? (
        <div className="mt-16 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-[500px] animate-pulse rounded-xl border bg-muted/20" />
          ))}
        </div>
      ) : (
        <div className="mt-16 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {plans?.map((plan) => <PlanCard key={plan.id} plan={plan} />)}
        </div>
      )}

      <div className="mt-16 rounded-xl border bg-card/40 p-8 text-center">
        <h2 className="text-2xl font-semibold">Still hiring a human receptionist?</h2>
        <p className="mx-auto mt-2 max-w-xl text-muted-foreground">
          An average receptionist costs &#8377;20,000+/month, works 8 hours, and takes leaves.
          VoxHelperAI works 24/7 at a fraction of the cost with zero sick days.
        </p>
      </div>

      <div className="mt-12 text-center text-sm text-muted-foreground">
        <p>No hidden charges. Cancel anytime. 24/7 support available.</p>
      </div>
    </div>
  );
}
