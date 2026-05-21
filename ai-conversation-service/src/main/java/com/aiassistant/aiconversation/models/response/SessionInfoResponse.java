package com.aiassistant.aiconversation.models.response;

import com.aiassistant.aiconversation.llm.TokenUsage;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
public class SessionInfoResponse {
    String conversationId;
    String businessId;
    String provider;
    boolean hasKnowledge;
    int messageCount;
    int pendingMessageCount;
    TokenUsage usage;
    Instant createdAt;
    Instant lastActivityAt;
}