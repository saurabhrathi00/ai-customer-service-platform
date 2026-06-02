package com.aiassistant.userbusiness.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
public class PhoneNumberResponse {
    String id;
    String businessId;
    String phoneNumber;
    String providerId;
    String providerSlug;
    String label;
    Boolean isActive;
    Instant createdAt;
}
