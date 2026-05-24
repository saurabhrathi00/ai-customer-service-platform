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
      </PageBody>
    </>
  );
}
