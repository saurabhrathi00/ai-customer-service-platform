package com.aiassistant.userbusiness.models.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadResponse {
    String id;
    String businessId;
    String callLogId;
    String leadType;          // APPOINTMENT | HIGH_INTEREST | HUMAN_REQUEST

    String customerPhone;
    String customerName;
    String callerLanguage;

    String summary;
    BigDecimal interestRating;

    // APPOINTMENT-only
    String service;
    String preferredWindowRaw;
    List<Map<String, Object>> structuredSlots;
    Instant suggestedDatetime;

    String status;            // NEW | APPROVED | DECLINED | IGNORED
    Instant confirmedDatetime;
    String declineReason;
    Instant decidedAt;
    String decidedVia;        // DASHBOARD | WHATSAPP

    Integer remindersSent;
    Instant lastReminderAt;
    Instant nextReminderAt;

    /** Denormalised so notification-service can read business context in
     *  one shot. Owner can change the WA number after a lead lands — the
     *  scheduler always reads the current value. */
    String businessName;
    String ownerWhatsappNumber;

    Instant ownerNotifiedAt;
    Instant customerNotifiedAt;

    Instant createdAt;
    Instant updatedAt;
}
