import { Mail, Phone, MessageCircle } from 'lucide-react';

export default function SupportPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6 lg:px-8">
      <h1 className="text-3xl font-bold">Support</h1>
      <p className="mt-2 text-muted-foreground">
        We&apos;re here to help. Reach out through any of the channels below.
      </p>

      <div className="mt-10 grid gap-6 sm:grid-cols-3">
        <div className="flex flex-col items-center rounded-xl border p-6 text-center">
          <Mail className="h-8 w-8 text-primary" />
          <h3 className="mt-3 font-semibold">Email</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            support@voxhelperai.com
          </p>
          <p className="mt-1 text-xs text-muted-foreground">We reply within 24 hours</p>
        </div>

        <div className="flex flex-col items-center rounded-xl border p-6 text-center">
          <Phone className="h-8 w-8 text-primary" />
          <h3 className="mt-3 font-semibold">Phone</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            +91-XXXXXXXXXX
          </p>
          <p className="mt-1 text-xs text-muted-foreground">Mon-Sat, 10 AM - 7 PM IST</p>
        </div>

        <div className="flex flex-col items-center rounded-xl border p-6 text-center">
          <MessageCircle className="h-8 w-8 text-primary" />
          <h3 className="mt-3 font-semibold">WhatsApp</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            +91-XXXXXXXXXX
          </p>
          <p className="mt-1 text-xs text-muted-foreground">Quick responses</p>
        </div>
      </div>

      <div className="mt-12 rounded-xl border bg-card/40 p-8">
        <h2 className="text-lg font-semibold">Frequently Asked Questions</h2>
        <div className="mt-4 space-y-4 text-sm">
          <div>
            <p className="font-medium">How long does setup take?</p>
            <p className="mt-1 text-muted-foreground">
              After payment, our team sets up your AI receptionist within 24 hours.
              This includes allocating a phone number and configuring channels.
            </p>
          </div>
          <div>
            <p className="font-medium">Can I change my plan?</p>
            <p className="mt-1 text-muted-foreground">
              Yes, contact support to upgrade or downgrade. Changes take effect from
              the next billing cycle.
            </p>
          </div>
          <div>
            <p className="font-medium">What happens when I exceed my call limit?</p>
            <p className="mt-1 text-muted-foreground">
              Extra calls beyond your plan limit are charged at the per-call rate
              specified in your plan. Your AI receptionist continues to work without
              interruption.
            </p>
          </div>
          <div>
            <p className="font-medium">Can I cancel anytime?</p>
            <p className="mt-1 text-muted-foreground">
              Yes. Your service continues until the end of the current billing period.
              No refunds are issued for the remaining period.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
