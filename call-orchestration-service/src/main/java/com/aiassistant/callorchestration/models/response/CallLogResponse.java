package com.aiassistant.callorchestration.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
public class CallLogResponse {
    String id;
    String businessId;
    String customerPhone;
    String customerName;
    String provider;
    String providerCallId;
    String queryType;
    String callSummary;
    String transcript;
    Integer callDurationSecs;
    Integer feedbackScore;
    Integer interestRating;
    Boolean callbackRequested;
    Instant callStartedAt;
    Instant callEndedAt;
}
