// Shared API DTO types — keep in sync with backend response DTOs.

export interface AuthenticationResponse {
  token: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  message: string;
}

export interface BusinessResponse {
  id: string;
  name: string;
  email: string;
  category: string | null;
  description: string | null;
  location: string | null;
  operatingHours: string | null;
  /** E.164 WhatsApp number for owner-facing lead notifications. */
  whatsappNumber: string | null;
  liveDemoSecondsRemaining: number;
  subscriptionStatus: string | null;
  subscriptionId: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface PhoneNumberResponse {
  id: string;
  businessId: string;
  phoneNumber: string;
  providerId: string;
  providerSlug: string | null;
  label: string | null;
  isActive: boolean;
  createdAt: string;
}

export type RatingSignalKey =
  | 'LONG_CALL'
  | 'POSITIVE_FEEDBACK'
  | 'CALLBACK_REQUESTED'
  | 'NEGATIVE_FEEDBACK'
  | 'SHORT_CALL'
  | 'AI_COULD_NOT_ANSWER';

export interface RatingConfigEntry {
  id: string;
  signalKey: RatingSignalKey;
  scoreValue: number;
  updatedAt: string;
}

export interface RatingConfigResponse {
  businessId: string;
  entries: RatingConfigEntry[];
}

export interface BusinessHours {
  days: Record<
    'mon' | 'tue' | 'wed' | 'thu' | 'fri' | 'sat' | 'sun',
    { open: string; close: string; closed: boolean }
  >;
  holidays: string[];
}

export interface ServiceOffered {
  name: string;
  description?: string;
  price_min?: number;
  price_max?: number;
  price_currency?: string;
  duration_minutes?: number;
}

export interface ProfileResponse {
  id: string;
  businessId: string;
  businessHours: BusinessHours | null;
  address: string | null;
  locationNotes: string | null;
  altPhone: string | null;
  contactEmail: string | null;
  websiteUrl: string | null;
  languagesSpoken: string[] | null;
  servicesOffered: ServiceOffered[] | null;
  paymentMethods: string[] | null;
  appointmentPolicy: string | null;
  cancellationPolicy: string | null;
  refundPolicy: string | null;
  completenessScore: number | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface CompletenessResponse {
  businessId: string;
  score: number;
  missingFields: string[];
}

export interface FaqResponse {
  id: string;
  businessId: string;
  question: string;
  answer: string;
  priority: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface FreeformResponse {
  businessId: string;
  content: string | null;
  updatedAt: string | null;
}

export type EscalationAction = 'TRANSFER' | 'CALLBACK' | 'DECLINE';

export interface EscalationRuleResponse {
  id: string;
  businessId: string;
  triggerPhrase: string;
  action: EscalationAction;
  actionMessage: string | null;
  isActive: boolean;
  createdAt: string;
}

// ---------- leads ----------
export type LeadType = 'APPOINTMENT' | 'HIGH_INTEREST' | 'HUMAN_REQUEST';
export type LeadStatus = 'NEW' | 'APPROVED' | 'DECLINED' | 'IGNORED';
export type ReminderMode = 'FIXED' | 'INCREMENT';

export interface StructuredSlot {
  date?: string;        // YYYY-MM-DD
  period?: 'MORNING' | 'AFTERNOON' | 'EVENING' | 'NIGHT' | 'ANY';
}

export interface LeadResponse {
  id: string;
  businessId: string;
  callLogId: string;
  leadType: LeadType;

  customerPhone: string | null;
  customerName: string | null;
  callerLanguage: string | null;

  summary: string | null;
  interestRating: number | null;

  // APPOINTMENT-only fields.
  service: string | null;
  preferredWindowRaw: string | null;
  structuredSlots: StructuredSlot[] | null;
  suggestedDatetime: string | null;

  status: LeadStatus;
  confirmedDatetime: string | null;
  declineReason: string | null;
  decidedAt: string | null;
  decidedVia: 'DASHBOARD' | 'WHATSAPP' | null;

  remindersSent: number;
  lastReminderAt: string | null;
  nextReminderAt: string | null;

  businessName: string | null;
  ownerWhatsappNumber: string | null;
  ownerNotifiedAt: string | null;
  customerNotifiedAt: string | null;

  createdAt: string;
  updatedAt: string | null;
}

export interface LeadNotificationSettingsResponse {
  businessId: string;
  highInterestThreshold: number;
  reminderMode: ReminderMode;
  reminderIntervalMinutes: number;
  maxReminders: number;
  updatedAt: string | null;
}

export interface CallSummaryResponse {
  id: string;
  callLogId: string;
  businessId: string;
  callerName: string | null;
  customerPhone: string | null;
  queryType: string | null;
  interestRating: number | null;
  interestReason: string | null;
  mainConcerns: string[] | null;
  callbackNeeded: boolean | null;
  callbackReason: string | null;
  unansweredQuestions: string[] | null;
  summaryText: string | null;
  createdAt: string;
  updatedAt: string | null;
}

// ---------- subscription ----------
export interface PlanResponse {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  priceMonthly: number;
  callsIncluded: number;
  maxCallDurationSec: number;
  channels: number;
  phoneNumbers: number;
  extraCallRate: number;
  features: Record<string, unknown>;
  isActive: boolean;
  displayOrder: number;
  isPopular: boolean;
  razorpayPlanId: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface CheckoutRequest {
  planSlug: string;
  businessId?: string;
  businessName?: string;
  ownerName?: string;
  email?: string;
  phone?: string;
}

export interface CheckoutResponse {
  subscriptionId: string;
  razorpaySubscriptionId: string;
  razorpayKeyId: string;
}

export interface SubscriptionResponse {
  id: string;
  businessId: string;
  plan: PlanResponse | null;
  status: string;
  razorpaySubscriptionId: string | null;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  callsUsed: number;
  minutesUsed: number;
  callsRemaining: number;
  daysRemaining: number;
  cancelAtPeriodEnd: boolean;
  cancelledAt: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface CallLogResponse {
  id: string;
  businessId: string;
  customerPhone: string | null;
  customerName: string | null;
  provider: string;
  providerCallId: string;
  queryType: string | null;
  callSummary: string | null;
  transcript: string | null;
  callDurationSecs: number | null;
  feedbackScore: number | null;
  interestRating: number | null;
  callbackRequested: boolean;
  callStartedAt: string;
  callEndedAt: string | null;
}
