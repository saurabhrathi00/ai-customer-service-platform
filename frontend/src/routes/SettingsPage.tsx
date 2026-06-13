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
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: 'easeOut', delay: 0 }}
        >
          <BusinessProfileCard />
        </motion.div>
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: 'easeOut', delay: 0.07 }}
        >
          <SubscriptionCard />
        </motion.div>
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: 'easeOut', delay: 0.14 }}
        >
          <PhoneNumbersCard />
        </motion.div>
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: 'easeOut', delay: 0.21 }}
        >
          <NotificationRecipientsCard />
        </motion.div>
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: 'easeOut', delay: 0.28 }}
        >
          <ChangePasswordCard />
        </motion.div>
        <motion.div
          className="lg:col-span-2"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: 'easeOut', delay: 0.35 }}
        >
          <RatingConfigCard />
        </motion.div>
      </PageBody>
    </>
  );
}
