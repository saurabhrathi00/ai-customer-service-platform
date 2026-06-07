export default function TermsPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6 lg:px-8">
      <h1 className="text-3xl font-bold">Terms and Conditions</h1>
      <p className="mt-2 text-sm text-muted-foreground">Last updated: June 2026</p>

      <div className="mt-8 space-y-6 text-sm leading-relaxed text-foreground/80">
        <section>
          <h2 className="text-lg font-semibold text-foreground">1. Introduction</h2>
          <p className="mt-2">
            These Terms and Conditions govern your use of VoxHelperAI (&quot;Service&quot;),
            an AI-powered voice receptionist platform operated by VoxHelperAI
            (&quot;Company&quot;, &quot;we&quot;, &quot;us&quot;). By subscribing to or
            using the Service, you agree to be bound by these terms.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">2. Service Description</h2>
          <p className="mt-2">
            VoxHelperAI provides an AI voice receptionist that handles incoming calls for
            your business, answers queries, books appointments, and provides post-call
            summaries. The service operates 24/7 based on the knowledge and configuration
            you provide.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">3. Subscription and Billing</h2>
          <ul className="mt-2 list-disc pl-5 space-y-1">
            <li>Subscriptions are billed monthly in Indian Rupees (INR) plus applicable GST (18%).</li>
            <li>Payment is processed through Razorpay. Auto-debit will be set up for monthly renewals.</li>
            <li>You may cancel your subscription at any time. Access continues until the end of the current billing period.</li>
            <li>Price changes apply to new billing cycles only and do not affect the current period.</li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">4. Usage Limits</h2>
          <p className="mt-2">
            Each plan includes a set number of calls per month. Calls exceeding the plan
            limit will be charged at the per-call overage rate specified in your plan.
            Maximum call duration limits apply as specified in your plan.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">5. Your Responsibilities</h2>
          <ul className="mt-2 list-disc pl-5 space-y-1">
            <li>Provide accurate business information for AI training.</li>
            <li>Ensure the information used by the AI is lawful and does not infringe third-party rights.</li>
            <li>Do not use the service for illegal, fraudulent, or abusive purposes.</li>
            <li>Maintain the confidentiality of your account credentials.</li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">6. Data and Privacy</h2>
          <p className="mt-2">
            Call data, transcripts, and summaries are stored securely and used solely
            to provide the service. Please refer to our Privacy Policy for details on
            data handling practices.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">7. Limitation of Liability</h2>
          <p className="mt-2">
            VoxHelperAI is an AI-based service and may not always provide perfectly
            accurate responses. We are not liable for any business losses resulting from
            AI-generated responses, missed calls due to technical issues, or actions
            taken based on AI recommendations.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">8. Termination</h2>
          <p className="mt-2">
            We reserve the right to suspend or terminate your account if you violate
            these terms, engage in abusive behaviour, or fail to make payment.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">9. Changes to Terms</h2>
          <p className="mt-2">
            We may update these terms from time to time. Continued use of the service
            after changes constitutes acceptance of the updated terms.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground">10. Governing Law</h2>
          <p className="mt-2">
            These terms are governed by the laws of India. Any disputes shall be subject
            to the exclusive jurisdiction of the courts in New Delhi, India.
          </p>
        </section>
      </div>
    </div>
  );
}
