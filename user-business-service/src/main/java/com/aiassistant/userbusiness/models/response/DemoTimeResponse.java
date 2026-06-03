package com.aiassistant.userbusiness.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class DemoTimeResponse {
    String businessId;
    int secondsRemaining;
}
