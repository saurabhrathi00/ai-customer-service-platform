export default function PrivacyPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6 lg:px-8">
      <h1 className="text-3xl font-bold">Privacy Policy</h1>
      <p className="mt-2 text-sm text-muted-foreground">Last updated: June 2026</p>

      <div className="mt-8 space-y-6 text-sm leading-relaxed text-foreground/80">
        <section>
          <h2 className="text-lg font-semibold text-foreground">1. Information We Collect</h2>
          <ul className="mt-2 list-disc pl-5 space-y-1">
            <li>
              <strong>Account information:</strong> Business name, owner name, email, phone number,
              and business category provided during registration.
            </li>
            <li>
              <strong>Call data:</strong> Phone numbers of callers, call recordings (if enabled),
              call transcripts, AI-generated summaries, and call metadata (duration, timestamps).
            </li>
            <li>
              <strong>Business knowledge:</strong> FAQs, business profile, operating hours,
              escalation rules, and other information you provide to train the AI.
            </li>
            <li>
              <strong>Payment information:</strong> Processed securely by Razorpay. We do not
              store card numbers or bank details on our servers.
            </li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">2. How We Use Your Information</h2>
          <ul className="mt-2 list-disc pl-5 space-y-1">
            <li>To provide and improve the AI receptionist service.</li>
            <li>To generate call summaries, lead notifications, and analytics.</li>
            <li>To process payments and manage subscriptions.</li>
            <li>To send service-related notifications (payment confirmations, alerts).</li>
            <li>To troubleshoot issues and provide customer support.</li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">3. Data Storage and Security</h2>
          <p className="mt-2">
            Your data is stored on secured servers with encryption at rest and in transit.
            Access is restricted to authorised personnel. We use industry-standard security
            practices including HTTPS, JWT authentication, and role-based access control.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">4. Data Sharing</h2>
          <p className="mt-2">
            We do not sell your data. We may share data with:
          </p>
          <ul className="mt-2 list-disc pl-5 space-y-1">
            <li>Third-party service providers (telephony, AI processing, payment) necessary
              to deliver the service.</li>
            <li>Law enforcement if required by law or legal process.</li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">5. Data Retention</h2>
          <p className="mt-2">
            Call data and business information are retained for the duration of your
            subscription plus 90 days after cancellation. You may request deletion of
            your data by contacting support.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">6. Your Rights</h2>
          <ul className="mt-2 list-disc pl-5 space-y-1">
            <li>Access and download your data.</li>
            <li>Request correction of inaccurate data.</li>
            <li>Request deletion of your data.</li>
            <li>Withdraw consent for data processing (may affect service availability).</li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">7. Contact Us</h2>
          <p className="mt-2">
            For privacy-related queries, contact us at support@voxhelperai.com.
          </p>
        </section>
      </div>
    </div>
  );
}
