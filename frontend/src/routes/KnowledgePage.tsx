import { motion } from 'framer-motion';
import { Brain } from 'lucide-react';

import { PageBody, PageHeader } from '@/components/app/AppLayout';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/Tabs';
import { ProfileTab } from '@/features/knowledge/ProfileTab';
import { FaqsTab } from '@/features/knowledge/FaqsTab';
import { FreeformTab } from '@/features/knowledge/FreeformTab';
import { EscalationsTab } from '@/features/knowledge/EscalationsTab';

export default function KnowledgePage() {
  return (
    <>
      <PageHeader
        title="Knowledge"
        subtitle="Teach your AI agent what your business knows."
      />
      <PageBody>
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut' }}
        >
          <div className="mb-4 flex items-center gap-2 text-xs text-muted-foreground">
            <Brain className="h-3.5 w-3.5 text-primary" />
            <span>Changes here update what your AI agent knows in real-time.</span>
          </div>
          <Tabs defaultValue="profile">
            <TabsList className="w-full sm:w-auto">
              <TabsTrigger value="profile">Profile</TabsTrigger>
              <TabsTrigger value="faqs">FAQs</TabsTrigger>
              <TabsTrigger value="freeform">Free-form</TabsTrigger>
              <TabsTrigger value="escalations">Escalations</TabsTrigger>
            </TabsList>
            <TabsContent value="profile">
              <ProfileTab />
            </TabsContent>
            <TabsContent value="faqs">
              <FaqsTab />
            </TabsContent>
            <TabsContent value="freeform">
              <FreeformTab />
            </TabsContent>
            <TabsContent value="escalations">
              <EscalationsTab />
            </TabsContent>
          </Tabs>
        </motion.div>
      </PageBody>
    </>
  );
}
