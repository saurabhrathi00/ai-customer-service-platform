import { motion } from 'framer-motion';

import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { BusinessProfileCard } from '@/features/settings/BusinessProfileCard';
import { ChangePasswordCard } from '@/features/settings/ChangePasswordCard';
import { NotificationRecipientsCard } from '@/features/settings/NotificationRecipientsCard';
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
        <motion.div
          className="contents"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut', delay: 0.05 }}
        >
          <BusinessProfileCard />
          <SubscriptionCard />
          <PhoneNumbersCard />
          <NotificationRecipientsCard />
          <ChangePasswordCard />
          <div className="lg:col-span-2">
            <RatingConfigCard />
          </div>
        </motion.div>
      </PageBody>
    </>
  );
}
