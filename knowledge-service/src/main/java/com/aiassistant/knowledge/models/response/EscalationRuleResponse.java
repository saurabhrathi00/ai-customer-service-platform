package com.aiassistant.knowledge.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
public class EscalationRuleResponse {
    String id;
    String businessId;
    String triggerPhrase;
    String action;
    String actionMessage;
    Boolean isActive;
    Instant createdAt;
}
