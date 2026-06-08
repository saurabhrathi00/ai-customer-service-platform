import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { subscription, business } from '@/api/resources';
import { useAuthStore } from '@/store/auth';
import { Button } from '@/components/ui/Button';
import { useState } from 'react';
import { toast } from 'sonner';
import { Check } from 'lucide-react';

function loadRazorpay(): Promise<void> {
  if (window.Razorpay) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://checkout.razorpay.com/v1/checkout.js';
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('Failed to load Razorpay'));
    document.head.appendChild(s);
  });
}

function formatPrice(paise: number): string {
  return new Intl.NumberFormat('en-IN').format(paise / 100);
}

declare global {
  interface Window {
    Razorpay: new (options: Record<string, unknown>) => { open: () => void };
  }
}

export default function CheckoutPage() {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const { businessId, email } = useAuthStore();
  const [success, setSuccess] = useState(false);

  const { data: plan, isLoading: planLoading } = useQuery({
    queryKey: ['plan', slug],
    queryFn: () => subscription.planBySlug(slug!),
    enabled: Boolean(slug),
  });

  const { data: profile, isLoading: profileLoading } = useQuery({
    queryKey: ['business', 'profile', businessId],
    queryFn: () => business.profile(businessId!),
    enabled: Boolean(businessId),
  });

  const checkoutMutation = useMutation({
    mutationFn: async () => {
      await loadRazorpay();
      return subscription.checkout({
        planSlug: slug!,
        businessId: businessId ?? undefined,
        businessName: profile?.name ?? '',
        email: profile?.email ?? email ?? '',
        phone: profile?.whatsappNumber ?? '',
      });
    },
    onSuccess: (data) => {
      if (!plan) return;
      const options = {
        key: data.razorpayKeyId,
        subscription_id: data.razorpaySubscriptionId,
        name: 'VoxAI',
        description: `${plan.name} Plan - Monthly Subscription`,
        prefill: {
          name: profile?.name ?? '',
          email: profile?.email ?? email ?? '',
          contact: profile?.whatsappNumber ?? '',
        },
        handler: () => {
          setSuccess(true);
          toast.success('Payment successful!');
        },
        modal: {
          ondismiss: () => toast.info('Payment cancelled'),
        },
        theme: { color: '#6366f1' },
      };
      const rzp = new window.Razorpay(options);
      rzp.open();
    },
    onError: (err: any) => {
      const msg = err?.response?.data?.message;
      if (msg?.includes('not synced with Razorpay') || msg?.includes('not configured') || msg?.includes('not available')) {
        toast.error('Online payments are not available yet. Please contact us to subscribe.');
      } else {
        toast.error('Failed to initiate checkout. Please try again.');
      }
    },
  });

  if (success) {
    return (
      <div className="mx-auto max-w-lg px-4 py-24 text-center">
        <div className="mx-auto mb-6 grid h-16 w-16 place-items-center rounded-full bg-green-100 text-green-600">
          <Check className="h-8 w-8" />
        </div>
        <h1 className="text-3xl font-bold">Payment Successful!</h1>
        <p className="mt-4 text-muted-foreground">
          Thank you for subscribing to VoxAI. Our team will set up your AI voice agent
          and contact you within 24 hours.
        </p>
        <Button className="mt-8" onClick={() => navigate('/')}>
          Go to Dashboard
        </Button>
      </div>
    );
  }

  if (planLoading || profileLoading) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16">
        <div className="h-[400px] animate-pulse rounded-xl border bg-muted/20" />
      </div>
    );
  }

  if (!plan) {
    return (
      <div className="mx-auto max-w-lg px-4 py-24 text-center">
        <h1 className="text-2xl font-semibold">Plan not found</h1>
        <Button variant="outline" className="mt-6" onClick={() => navigate('/pricing')}>
          View all plans
        </Button>
      </div>
    );
  }

  const basePrice = plan.priceMonthly;
  const gstAmount = Math.round(basePrice * 0.18);
  const totalAmount = basePrice + gstAmount;

  return (
    <div className="mx-auto max-w-xl px-4 py-16 sm:px-6">
      <h1 className="text-3xl font-bold">Checkout</h1>
      <p className="mt-2 text-muted-foreground">Subscribe to the {plan.name} plan</p>

      <div className="mt-8 space-y-6">
        {profile && (
          <div className="rounded-xl border bg-card/40 p-5">
            <h2 className="text-sm font-medium text-muted-foreground">Subscribing as</h2>
            <p className="mt-1 text-lg font-semibold">{profile.name}</p>
            <p className="text-sm text-muted-foreground">{profile.email}</p>
          </div>
        )}

        <div className="rounded-xl border bg-card/40 p-5">
          <h2 className="text-lg font-semibold">Order Summary</h2>
          <div className="mt-4 space-y-2 text-sm">
            <div className="flex justify-between">
              <span>{plan.name} Plan</span>
              <span>&#8377;{formatPrice(basePrice)}</span>
            </div>
            <div className="flex justify-between text-muted-foreground">
              <span>GST (18%)</span>
              <span>&#8377;{formatPrice(gstAmount)}</span>
            </div>
            <hr />
            <div className="flex justify-between font-semibold text-base">
              <span>Total</span>
              <span>&#8377;{formatPrice(totalAmount)}/month</span>
            </div>
          </div>
          <ul className="mt-4 space-y-1.5 text-xs text-muted-foreground">
            <li>{plan.callsIncluded} calls/month included</li>
            <li>{plan.channels} concurrent channel{plan.channels > 1 ? 's' : ''}</li>
            <li>Cancel anytime</li>
          </ul>
        </div>

        <Button
          className="w-full"
          size="lg"
          onClick={() => checkoutMutation.mutate()}
          disabled={checkoutMutation.isPending}
        >
          {checkoutMutation.isPending ? 'Processing...' : `Pay ₹${formatPrice(totalAmount)}`}
        </Button>
      </div>
    </div>
  );
}
