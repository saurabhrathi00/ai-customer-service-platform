import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { BusinessProfileCard } from '@/features/settings/BusinessProfileCard';
import { ChangePasswordCard } from '@/features/settings/ChangePasswordCard';
import { PhoneNumbersCard } from '@/features/settings/PhoneNumbersCard';
import { RatingConfigCard } from '@/features/settings/RatingConfigCard';
import { SubscriptionCard } from '@/features/settings/SubscriptionCard';

export default function SettingsPage() {
  return (
    <>
      <PageHeader
        title="Settings"
        subtitle="Numbers, scoring, and the basics of your business."
      />
      <PageBody className="grid gap-6 lg:grid-cols-2">
        <BusinessProfileCard />
        <SubscriptionCard />
        <PhoneNumbersCard />
        <ChangePasswordCard />
        <div className="lg:col-span-2">
          <RatingConfigCard />
        </div>
      </PageBody>
    </>
  );
}
