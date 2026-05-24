package com.aiassistant.userbusiness.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
@Jacksonized
public class LeadNotificationSettingsResponse {
    String businessId;
    BigDecimal highInterestThreshold;
    String reminderMode;          // FIXED | INCREMENT
    Integer reminderIntervalMinutes;
    Integer maxReminders;
    Instant updatedAt;
}
