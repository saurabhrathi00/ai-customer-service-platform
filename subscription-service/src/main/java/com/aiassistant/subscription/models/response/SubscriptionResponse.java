package com.aiassistant.subscription.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
public class SubscriptionResponse {
    String id;
    String businessId;
    PlanResponse plan;
    String status;
    String razorpaySubscriptionId;
    Instant currentPeriodStart;
    Instant currentPeriodEnd;
    int callsUsed;
    int minutesUsed;
    int callsRemaining;
    int daysRemaining;
    boolean cancelAtPeriodEnd;
    Instant cancelledAt;
    Instant createdAt;
    Instant updatedAt;
}
