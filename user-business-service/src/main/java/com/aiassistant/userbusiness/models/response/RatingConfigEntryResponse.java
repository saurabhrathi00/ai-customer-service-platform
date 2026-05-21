package com.aiassistant.userbusiness.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
public class RatingConfigEntryResponse {
    String id;
    String signalKey;
    Integer scoreValue;
    Instant updatedAt;
}
