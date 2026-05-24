package com.aiassistant.summary.models.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

/**
 * Tenant-facing projection of {@code call_summaries}. Returned by
 * {@code GET /api/v1/summaries/{businessId}}. Field names match the entity
 * so the dashboard can render structured data (concerns, callback reason,
 * unanswered questions) directly.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallSummaryResponse {
    String id;
    String callLogId;
    String businessId;
    String callerName;
    String customerPhone;
    String queryType;
    Integer interestRating;
    String interestReason;
    List<String> mainConcerns;
    Boolean callbackNeeded;
    String callbackReason;
    List<String> unansweredQuestions;
    String summaryText;
    Instant createdAt;
    Instant updatedAt;
}
