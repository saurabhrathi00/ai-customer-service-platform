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
  isActive: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface PhoneNumberResponse {
  id: string;
  businessId: string;
  twilioNumber: string;
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
