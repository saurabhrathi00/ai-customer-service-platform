package com.aiassistant.userbusiness.models.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationRecipientResponse {
    String id;
    String whatsappNumber;
    String label;
    Boolean isActive;
    Instant createdAt;
}
