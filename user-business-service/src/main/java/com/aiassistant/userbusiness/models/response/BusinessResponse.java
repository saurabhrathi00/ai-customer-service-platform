package com.aiassistant.userbusiness.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
public class BusinessResponse {
    String id;
    String name;
    String email;
    String category;
    String description;
    String location;
    String operatingHours;
    /** Owner's WhatsApp number (E.164). Used for appointment-request pings.
     *  Null until owner sets it in settings. */
    String whatsappNumber;
    Integer liveDemoSecondsRemaining;
    String subscriptionStatus;
    String subscriptionId;
    Boolean isActive;
    Instant createdAt;
    Instant updatedAt;
}
