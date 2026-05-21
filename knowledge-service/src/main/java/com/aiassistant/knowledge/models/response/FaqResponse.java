package com.aiassistant.knowledge.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
public class FaqResponse {
    String id;
    String businessId;
    String question;
    String answer;
    Integer priority;
    Boolean isActive;
    Instant createdAt;
    Instant updatedAt;
}
