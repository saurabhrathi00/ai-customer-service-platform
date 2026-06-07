import { authApi, businessApi, callsApi, knowledgeApi, subscriptionApi, summaryApi } from './client';
import type {
  AuthenticationResponse,
  BusinessResponse,
  CallLogResponse,
  CallSummaryResponse,
  CheckoutRequest,
  CheckoutResponse,
  CompletenessResponse,
  EscalationAction,
  EscalationRuleResponse,
  FaqResponse,
  FreeformResponse,
  LeadNotificationSettingsResponse,
  LeadResponse,
  PhoneNumberResponse,
  PlanResponse,
  ProfileResponse,
  RatingConfigResponse,
  RatingSignalKey,
  ReminderMode,
  SubscriptionResponse,
} from '@/types/api';

// ---------- auth ----------
export const auth = {
  signin: (email: string, password: string) =>
    authApi.post<AuthenticationResponse>('/auth/signin', { email, password }).then((r) => r.data),

  changePassword: (email: string, currentPassword: string, newPassword: string) =>
    authApi
      .post<{ message: string }>('/auth/change-password', { email, currentPassword, newPassword })
      .then((r) => r.data),
};

// ---------- business ----------
export const business = {
  register: (input: {
    name: string;
    email: string;
    password: string;
    category?: string;
    description?: string;
    location?: string;
    operatingHours?: string;
    whatsappNumber?: string;
  }) => businessApi.post<BusinessResponse>('/business/register', input).then((r) => r.data),

  profile: (id: string) =>
    businessApi.get<BusinessResponse>(`/business/${id}/profile`).then((r) => r.data),

  updateProfile: (id: string, patch: Partial<Omit<BusinessResponse, 'id' | 'email' | 'isActive' | 'createdAt' | 'updatedAt'>>) =>
    businessApi.put<BusinessResponse>(`/business/${id}/profile`, patch).then((r) => r.data),

  phoneNumbers: (id: string) =>
    businessApi.get<PhoneNumberResponse[]>(`/business/${id}/phone-numbers`).then((r) => r.data),

  addPhoneNumber: (id: string, input: { phoneNumber: string; providerSlug: string; label?: string }) =>
    businessApi
      .post<PhoneNumberResponse>(`/business/${id}/phone-numbers`, input)
      .then((r) => r.data),

  removePhoneNumber: (id: string, numberId: string) =>
    businessApi.delete<void>(`/business/${id}/phone-numbers/${numberId}`).then((r) => r.data),

  ratingConfig: (id: string) =>
    businessApi.get<RatingConfigResponse>(`/business/${id}/rating-config`).then((r) => r.data),

  updateRatingConfig: (
    id: string,
    entries: { signalKey: RatingSignalKey; scoreValue: number }[],
  ) =>
    businessApi
      .put<RatingConfigResponse>(`/business/${id}/rating-config`, { entries })
      .then((r) => r.data),
};

// ---------- knowledge ----------
export const knowledge = {
  profile: (id: string) =>
    knowledgeApi.get<ProfileResponse>(`/knowledge/${id}/profile`).then((r) => r.data),

  upsertProfile: (id: string, patch: Partial<ProfileResponse>) =>
    knowledgeApi.put<ProfileResponse>(`/knowledge/${id}/profile`, patch).then((r) => r.data),

  completeness: (id: string) =>
    knowledgeApi
      .get<CompletenessResponse>(`/knowledge/${id}/completeness`)
      .then((r) => r.data),

  faqs: (id: string) =>
    knowledgeApi.get<FaqResponse[]>(`/knowledge/${id}/faqs`).then((r) => r.data),

  addFaq: (id: string, input: { question: string; answer: string; priority?: number; isActive?: boolean }) =>
    knowledgeApi.post<FaqResponse>(`/knowledge/${id}/faqs`, input).then((r) => r.data),

  updateFaq: (id: string, faqId: string, input: { question: string; answer: string; priority?: number; isActive?: boolean }) =>
    knowledgeApi.put<FaqResponse>(`/knowledge/${id}/faqs/${faqId}`, input).then((r) => r.data),

  deleteFaq: (id: string, faqId: string) =>
    knowledgeApi.delete<void>(`/knowledge/${id}/faqs/${faqId}`).then((r) => r.data),

  freeform: (id: string) =>
    knowledgeApi.get<FreeformResponse>(`/knowledge/${id}/freeform`).then((r) => r.data),

  updateFreeform: (id: string, content: string) =>
    knowledgeApi
      .put<FreeformResponse>(`/knowledge/${id}/freeform`, { content })
      .then((r) => r.data),

  escalations: (id: string) =>
    knowledgeApi
      .get<EscalationRuleResponse[]>(`/knowledge/${id}/escalations`)
      .then((r) => r.data),

  addEscalation: (
    id: string,
    input: { triggerPhrase: string; action: EscalationAction; actionMessage?: string; isActive?: boolean },
  ) =>
    knowledgeApi
      .post<EscalationRuleResponse>(`/knowledge/${id}/escalations`, input)
      .then((r) => r.data),

  updateEscalation: (
    id: string,
    ruleId: string,
    input: { triggerPhrase: string; action: EscalationAction; actionMessage?: string; isActive?: boolean },
  ) =>
    knowledgeApi
      .put<EscalationRuleResponse>(`/knowledge/${id}/escalations/${ruleId}`, input)
      .then((r) => r.data),

  deleteEscalation: (id: string, ruleId: string) =>
    knowledgeApi.delete<void>(`/knowledge/${id}/escalations/${ruleId}`).then((r) => r.data),
};

// ---------- calls ----------
export const calls = {
  recent: (businessId: string) =>
    callsApi.get<CallLogResponse[]>(`/calls/${businessId}/recent`).then((r) => r.data),

  delete: (businessId: string, callId: string) =>
    callsApi.delete<void>(`/calls/${businessId}/${callId}`).then((r) => r.data),

  deleteBulk: (businessId: string, callIds: string[]) =>
    callsApi
      .delete<{ deleted: number }>(`/calls/${businessId}/bulk`, { data: callIds })
      .then((r) => r.data),
};

// ---------- leads ----------
export const leads = {
  list: (businessId: string) =>
    businessApi.get<LeadResponse[]>(`/business/${businessId}/leads`).then((r) => r.data),

  get: (businessId: string, leadId: string) =>
    businessApi
      .get<LeadResponse>(`/business/${businessId}/leads/${leadId}`)
      .then((r) => r.data),

  approve: (businessId: string, leadId: string, confirmedDatetime?: string | null) =>
    businessApi
      .post<LeadResponse>(`/business/${businessId}/leads/${leadId}/approve`,
        confirmedDatetime ? { confirmedDatetime } : {})
      .then((r) => r.data),

  decline: (businessId: string, leadId: string, reason: string) =>
    businessApi
      .post<LeadResponse>(`/business/${businessId}/leads/${leadId}/decline`, { reason })
      .then((r) => r.data),

  ignore: (businessId: string, leadId: string) =>
    businessApi
      .post<LeadResponse>(`/business/${businessId}/leads/${leadId}/ignore`)
      .then((r) => r.data),
};

export const leadSettings = {
  get: (businessId: string) =>
    businessApi
      .get<LeadNotificationSettingsResponse>(`/business/${businessId}/lead-settings`)
      .then((r) => r.data),

  update: (
    businessId: string,
    patch: Partial<{
      highInterestThreshold: number;
      reminderMode: ReminderMode;
      reminderIntervalMinutes: number;
      maxReminders: number;
    }>,
  ) =>
    businessApi
      .put<LeadNotificationSettingsResponse>(`/business/${businessId}/lead-settings`, patch)
      .then((r) => r.data),
};

// ---------- subscription ----------
export const subscription = {
  plans: () =>
    subscriptionApi.get<PlanResponse[]>('/plans').then((r) => r.data),

  planBySlug: (slug: string) =>
    subscriptionApi.get<PlanResponse>(`/plans/${slug}`).then((r) => r.data),

  checkout: (input: CheckoutRequest) =>
    subscriptionApi.post<CheckoutResponse>('/subscriptions/checkout', input).then((r) => r.data),

  current: (businessId: string) =>
    subscriptionApi.get<SubscriptionResponse>(`/subscriptions/${businessId}/current`)
      .then((r) => r.data)
      .catch((err) => {
        if (err.response?.status === 404) return null;
        throw err;
      }),

  cancel: (businessId: string) =>
    subscriptionApi.post<SubscriptionResponse>(`/subscriptions/${businessId}/cancel`).then((r) => r.data),
};

// ---------- summaries ----------
export const summaries = {
  /** Newest-first list of every summary for a business. Joined client-side
   *  with the recent-calls list on the dashboard. */
  list: (businessId: string) =>
    summaryApi.get<CallSummaryResponse[]>(`/summaries/${businessId}`).then((r) => r.data),
};
