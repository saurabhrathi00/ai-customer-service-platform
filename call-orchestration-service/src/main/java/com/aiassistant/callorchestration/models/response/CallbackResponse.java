package com.aiassistant.callorchestration.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
public class CallbackResponse {
    String callId;
    String businessId;
    String customerPhone;
    String customerName;
    String callSummary;
    Instant callStartedAt;
}
