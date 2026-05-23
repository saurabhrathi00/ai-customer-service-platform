package com.aiassistant.callorchestration.models.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

/**
 * Returned by {@code GET /api/internal/calls/{callLogId}/transcript}.
 * Reconstitutes the conversation history plus the caller / business
 * context conversation-summary-service needs to write a useful summary.
 *
 * <p>Lives here as a peer of {@link CallLogResponse} — separate so we can
 * change one without breaking the other.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TranscriptPayload {
    String callLogId;
    String businessId;
    String businessName;
    String customerPhone;
    String language;
    String knowledge;
    /** Each entry: {@code {"role": "user"|"assistant", "content": "..."}}. */
    List<Map<String, String>> history;
}
