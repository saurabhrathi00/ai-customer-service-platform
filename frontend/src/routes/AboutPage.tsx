import { Phone, Mail, MessageCircle } from 'lucide-react';
import { COMPANY } from '@/lib/constants';

export default function AboutPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6 lg:px-8">
      <h1 className="text-3xl font-bold">About VoxAI</h1>
      <p className="mt-4 text-lg text-muted-foreground">
        We build AI voice agents so Indian businesses never miss a customer call again.
      </p>

      <div className="mt-10 space-y-8">
        <section>
          <h2 className="text-xl font-semibold">Our Mission</h2>
          <p className="mt-2 text-muted-foreground">
            Every missed call is a missed customer. For small businesses — clinics, salons, restaurants,
            hotels, law firms — hiring full-time staff to handle calls is expensive, and they still can&apos;t
            answer 24/7. VoxAI solves this with an AI voice agent that picks up every call,
            speaks Hindi and English naturally, captures leads, sells your services, and never takes a day off.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold">What We Do</h2>
          <ul className="mt-2 space-y-2 text-muted-foreground">
            <li>Answer every incoming call with a natural, human-like AI voice</li>
            <li>Handle FAQs, promote your services, and capture leads</li>
            <li>Send you instant WhatsApp notifications and post-call summaries</li>
            <li>Work 24/7 — nights, weekends, and holidays included</li>
            <li>Cost a fraction of hiring dedicated staff</li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Why Choose Us</h2>
          <div className="mt-4 grid gap-4 sm:grid-cols-3">
            <div className="rounded-xl border p-5 text-center">
              <p className="text-3xl font-bold text-primary">24/7</p>
              <p className="mt-1 text-sm text-muted-foreground">Always available</p>
            </div>
            <div className="rounded-xl border p-5 text-center">
              <p className="text-3xl font-bold text-primary">2+</p>
              <p className="mt-1 text-sm text-muted-foreground">Languages supported</p>
            </div>
            <div className="rounded-xl border p-5 text-center">
              <p className="text-3xl font-bold text-primary">&#8377;7K</p>
              <p className="mt-1 text-sm text-muted-foreground">Starting monthly</p>
            </div>
          </div>
        </section>

        <section className="rounded-xl border bg-card/40 p-8">
          <h2 className="text-xl font-semibold">Get in Touch</h2>
          <p className="mt-2 text-muted-foreground">
            Have questions or want a demo? Reach out to us directly.
          </p>
          <div className="mt-4 space-y-3 text-sm">
            <div className="flex items-center gap-3">
              <Phone className="h-4 w-4 text-primary" />
              <a href={`tel:${COMPANY.phoneRaw}`} className="hover:text-primary transition-colors">
                {COMPANY.phone}
              </a>
            </div>
            <div className="flex items-center gap-3">
              <Mail className="h-4 w-4 text-primary" />
              <a href={`mailto:${COMPANY.email}`} className="hover:text-primary transition-colors">
                {COMPANY.email}
              </a>
            </div>
            <div className="flex items-center gap-3">
              <MessageCircle className="h-4 w-4 text-primary" />
              <a href={COMPANY.whatsappUrl} target="_blank" rel="noopener noreferrer" className="hover:text-primary transition-colors">
                WhatsApp: {COMPANY.whatsapp}
              </a>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
