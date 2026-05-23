package com.aiassistant.summary.models.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Lightweight trigger from call-orchestration-service. We use the id to
 * fetch everything else we need (transcript + business knowledge + caller
 * metadata) directly from call-orch on a background thread.
 */
@Data
public class TriggerSummaryRequest {
    @NotBlank
    private String callLogId;
}
