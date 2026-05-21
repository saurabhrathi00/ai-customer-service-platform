package com.aiassistant.knowledge.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
public class FreeformResponse {
    String businessId;
    String content;
    Instant updatedAt;
}
