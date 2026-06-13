import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Check, X, Sparkles, Zap, Clock, PhoneCall } from 'lucide-react';
import { motion } from 'framer-motion';
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

const TRUST_ITEMS = [
  { icon: Clock, text: '24/7 uptime, no sick days' },
  { icon: PhoneCall, text: 'Answers in under 1 second' },
  { icon: Zap, text: 'Powered by Gemini AI' },
];

function PlanCard({ plan, index }: { plan: PlanResponse; index: number }) {
  const navigate = useNavigate();
  const features = plan.features as Record<string, unknown>;
  const languages = (features.languages as string[]) ?? ['Hindi', 'English'];

  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.1, duration: 0.45, ease: 'easeOut' }}
      className={`relative flex flex-col rounded-2xl border p-6 transition-all duration-300 ${
        plan.isPopular
          ? 'border-primary/60 bg-card shadow-2xl shadow-primary/10 scale-[1.02]'
          : 'border-border/50 bg-card/60 hover:border-primary/30 hover:shadow-lg hover:shadow-primary/5'
      }`}
    >
      {/* Popular glow */}
      {plan.isPopular && (
        <div className="absolute inset-0 rounded-2xl bg-gradient-to-b from-primary/8 via-transparent to-transparent pointer-events-none" />
      )}

      {plan.isPopular && (
        <div className="absolute -top-3.5 left-1/2 -translate-x-1/2 flex items-center gap-1.5 rounded-full bg-primary px-3.5 py-1 text-xs font-semibold text-primary-foreground shadow-lg shadow-primary/30">
          <Sparkles className="h-3 w-3" />
          Most Popular
        </div>
      )}

      <div className="relative z-10">
        <h3 className="text-lg font-bold">{plan.name}</h3>
        {plan.description && (
          <p className="mt-1 text-sm text-muted-foreground">{plan.description}</p>
        )}

        <div className="mt-5 flex items-baseline gap-1">
          <span className="text-4xl font-extrabold tracking-tight">
            &#8377;{formatPrice(plan.priceMonthly)}
          </span>
          <span className="text-muted-foreground text-sm">/month</span>
        </div>
        <p className="mt-1 text-xs text-muted-foreground">+ 18% GST</p>

        <ul className="mt-6 flex-1 space-y-2.5 text-sm">
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
          className={`mt-6 w-full ${plan.isPopular ? 'shadow-lg shadow-primary/25' : ''}`}
          size="lg"
          variant={plan.isPopular ? 'default' : 'outline'}
          onClick={() => navigate(`/checkout/${plan.slug}`)}
        >
          Get Started
        </Button>
      </div>
    </motion.div>
  );
}

function FeatureLine({ ok, children }: { ok?: boolean; children: React.ReactNode }) {
  return (
    <li className="flex items-center gap-2.5">
      {ok ? (
        <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-success/15">
          <Check className="h-2.5 w-2.5 text-success" />
        </span>
      ) : (
        <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-muted">
          <X className="h-2.5 w-2.5 text-muted-foreground/40" />
        </span>
      )}
      <span className={ok ? '' : 'text-muted-foreground/50'}>{children}</span>
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
    <div className="relative overflow-hidden">
      {/* Aurora background */}
      <div className="absolute inset-0 aurora-bg pointer-events-none" />
      <div className="absolute inset-0 dot-grid opacity-40 pointer-events-none" />
      {/* Top glow */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 h-72 w-[600px] rounded-full bg-primary/10 blur-3xl pointer-events-none" />

      <div className="relative mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">

        {/* Header */}
        <motion.div
          className="text-center"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
        >
          <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-primary/30 bg-primary/10 px-4 py-1.5 text-xs font-semibold text-primary">
            <Sparkles className="h-3 w-3" />
            AI-powered voice agents
          </div>
          <h1 className="text-4xl font-extrabold tracking-tight sm:text-5xl">
            Simple, transparent{' '}
            <span className="gradient-text">pricing</span>
          </h1>
          <p className="mx-auto mt-4 max-w-2xl text-lg text-muted-foreground">
            Your AI voice agent works 24/7, never takes a day off, and costs a fraction of
            hiring staff. Cancel anytime.
          </p>

          {/* Trust strip */}
          <div className="mt-8 flex flex-wrap justify-center gap-6">
            {TRUST_ITEMS.map(({ icon: Icon, text }) => (
              <div key={text} className="flex items-center gap-2 text-sm text-muted-foreground">
                <Icon className="h-4 w-4 text-primary" />
                {text}
              </div>
            ))}
          </div>
        </motion.div>

        {/* Plan cards */}
        {isLoading ? (
          <div className="mt-16 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-[520px] animate-pulse rounded-2xl border border-border/30 bg-card/30" />
            ))}
          </div>
        ) : (
          <div className="mt-16 grid gap-6 sm:grid-cols-2 lg:grid-cols-3 items-start">
            {plans?.map((plan, i) => <PlanCard key={plan.id} plan={plan} index={i} />)}
          </div>
        )}

        {/* CTA section */}
        <motion.div
          className="relative mt-20 overflow-hidden rounded-2xl border border-primary/20 bg-card/60 p-6 sm:p-10 text-center backdrop-blur-sm"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5, duration: 0.5 }}
        >
          <div className="absolute inset-0 bg-gradient-to-br from-primary/5 via-transparent to-blue-500/5 pointer-events-none" />
          <div className="relative z-10">
            <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-warning/30 bg-warning/10 px-3 py-1 text-xs font-semibold text-warning">
              <Zap className="h-3 w-3" />
              Still relying on staff?
            </div>
            <h2 className="text-2xl font-bold">Cut costs, not quality</h2>
            <p className="mx-auto mt-3 max-w-xl text-muted-foreground">
              Dedicated call staff costs &#8377;20,000+/month, works 8 hours, and takes leaves.
              VoxAI works 24/7 at a fraction of the cost — answering calls, selling your
              services, and capturing leads while you sleep.
            </p>
          </div>
        </motion.div>

        <motion.p
          className="mt-10 text-center text-sm text-muted-foreground"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.7 }}
        >
          No hidden charges · Cancel anytime · 24/7 support available
        </motion.p>
      </div>
    </div>
  );
}
