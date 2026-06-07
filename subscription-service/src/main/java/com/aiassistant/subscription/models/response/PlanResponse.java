package com.aiassistant.subscription.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
@Jacksonized
public class PlanResponse {
    String id;
    String name;
    String slug;
    String description;
    int priceMonthly;
    int callsIncluded;
    int maxCallDurationSec;
    int channels;
    int phoneNumbers;
    int extraCallRate;
    Map<String, Object> features;
    boolean isActive;
    int displayOrder;
    boolean isPopular;
    String razorpayPlanId;
    Instant createdAt;
    Instant updatedAt;
}
