package com.aiassistant.summary.models.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Payload posted to {@code user-business-service}'s
 * {@code POST /api/internal/leads}. Mirrors the receiving DTO. {@code leadType}
 * is nullable: when summary-service only knows the interest rating (not an
 * explicit appointment / human-request signal), it leaves type null and the
 * receiving service decides via its configured threshold.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateLeadRequest {
    String businessId;
    String callLogId;
    String leadType;          // APPOINTMENT | HUMAN_REQUEST | null (let UBS decide)
    String customerPhone;
    String customerName;
    String callerLanguage;
    String summary;
    BigDecimal interestRating;
    String service;
    String preferredWindowRaw;
    List<Map<String, Object>> structuredSlots;
    Instant suggestedDatetime;
}
