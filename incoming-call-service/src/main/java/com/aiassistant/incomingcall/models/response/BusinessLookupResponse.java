package com.aiassistant.incomingcall.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class BusinessLookupResponse {
    String businessId;
    String name;
    String twilioNumber;
    Boolean isActive;
}
