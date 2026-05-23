package com.aiassistant.summary.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Returned by call-orchestration-service's
 * {@code GET /api/internal/calls/{callLogId}/transcript} endpoint. Carries
 * the full conversation plus the caller/business context the LLM needs to
 * write a useful summary.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranscriptPayload {
    private String callLogId;
    private String businessId;
    private String businessName;
    private String customerPhone;
    private String language;
    private String knowledge;
    private List<Map<String, String>> history;
}
