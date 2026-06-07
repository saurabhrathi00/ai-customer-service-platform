export default function RefundPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6 lg:px-8">
      <h1 className="text-3xl font-bold">Refund &amp; Cancellation Policy</h1>
      <p className="mt-2 text-sm text-muted-foreground">Last updated: June 2026</p>

      <div className="mt-8 space-y-6 text-sm leading-relaxed text-foreground/80">
        <section>
          <h2 className="text-lg font-semibold text-foreground">No Refund Policy</h2>
          <p className="mt-2">
            VoxHelperAI operates on a <strong>no-refund policy</strong>. All payments made
            for subscriptions are non-refundable. This includes monthly subscription fees,
            overage charges, and any other payments made through our platform.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">Cancellation</h2>
          <ul className="mt-2 list-disc pl-5 space-y-1">
            <li>You may cancel your subscription at any time from your dashboard settings.</li>
            <li>Upon cancellation, your service will continue until the end of the current
              billing period.</li>
            <li>No partial refunds will be issued for unused portions of a billing period.</li>
            <li>After the current billing period ends, auto-renewal will stop and the
              service will be deactivated.</li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">Service Setup</h2>
          <p className="mt-2">
            After payment, our team will set up your AI receptionist (phone number
            allocation, channel configuration) within 24 hours. Once setup is complete,
            your service will go live.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">Contact</h2>
          <p className="mt-2">
            If you have questions about billing or cancellation, reach out to us at
            support@voxhelperai.com or call +91-XXXXXXXXXX.
          </p>
        </section>
      </div>
    </div>
  );
}
