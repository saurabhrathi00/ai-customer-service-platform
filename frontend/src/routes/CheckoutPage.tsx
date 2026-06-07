import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { subscription } from '@/api/resources';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
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

const schema = z.object({
  businessName: z.string().min(2, 'Business name is required'),
  ownerName: z.string().min(2, 'Owner name is required'),
  email: z.string().email('Valid email is required'),
  phone: z.string().min(10, 'Phone number is required'),
});
type FormData = z.infer<typeof schema>;

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
  const [success, setSuccess] = useState(false);

  const { data: plan, isLoading } = useQuery({
    queryKey: ['plan', slug],
    queryFn: () => subscription.planBySlug(slug!),
    enabled: Boolean(slug),
  });

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const checkoutMutation = useMutation({
    mutationFn: async (input: Parameters<typeof subscription.checkout>[0]) => {
      await loadRazorpay();
      return subscription.checkout(input);
    },
    onSuccess: (data) => {
      if (!plan) return;
      const options = {
        key: data.razorpayKeyId,
        subscription_id: data.razorpaySubscriptionId,
        name: 'VoxHelperAI',
        description: `${plan.name} Plan - Monthly Subscription`,
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
    onError: () => {
      toast.error('Failed to initiate checkout. Please try again.');
    },
  });

  function onSubmit(data: FormData) {
    checkoutMutation.mutate({
      planSlug: slug!,
      businessName: data.businessName,
      ownerName: data.ownerName,
      email: data.email,
      phone: data.phone,
    });
  }

  if (success) {
    return (
      <div className="mx-auto max-w-lg px-4 py-24 text-center">
        <div className="mx-auto mb-6 grid h-16 w-16 place-items-center rounded-full bg-green-100 text-green-600">
          <Check className="h-8 w-8" />
        </div>
        <h1 className="text-3xl font-bold">Payment Successful!</h1>
        <p className="mt-4 text-muted-foreground">
          Thank you for subscribing to VoxHelperAI. Our team will set up your AI receptionist
          and contact you within 24 hours.
        </p>
        <Button className="mt-8" onClick={() => navigate('/login')}>
          Go to Dashboard
        </Button>
      </div>
    );
  }

  if (isLoading) {
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
    <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6">
      <h1 className="text-3xl font-bold">Checkout</h1>
      <p className="mt-2 text-muted-foreground">Subscribe to the {plan.name} plan</p>

      <div className="mt-8 grid gap-8 md:grid-cols-5">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 md:col-span-3">
          <h2 className="text-lg font-semibold">Business Details</h2>

          <div>
            <Label htmlFor="businessName">Business Name</Label>
            <Input id="businessName" {...register('businessName')} />
            {errors.businessName && (
              <p className="mt-1 text-xs text-destructive">{errors.businessName.message}</p>
            )}
          </div>

          <div>
            <Label htmlFor="ownerName">Owner Name</Label>
            <Input id="ownerName" {...register('ownerName')} />
            {errors.ownerName && (
              <p className="mt-1 text-xs text-destructive">{errors.ownerName.message}</p>
            )}
          </div>

          <div>
            <Label htmlFor="email">Email</Label>
            <Input id="email" type="email" {...register('email')} />
            {errors.email && (
              <p className="mt-1 text-xs text-destructive">{errors.email.message}</p>
            )}
          </div>

          <div>
            <Label htmlFor="phone">Phone</Label>
            <Input id="phone" type="tel" {...register('phone')} />
            {errors.phone && (
              <p className="mt-1 text-xs text-destructive">{errors.phone.message}</p>
            )}
          </div>

          <Button
            type="submit"
            className="w-full"
            size="lg"
            disabled={checkoutMutation.isPending}
          >
            {checkoutMutation.isPending ? 'Processing...' : `Pay ₹${formatPrice(totalAmount)}`}
          </Button>
        </form>

        <div className="rounded-xl border bg-card/40 p-5 md:col-span-2 h-fit">
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
            <li>Cancel anytime, no refund</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
