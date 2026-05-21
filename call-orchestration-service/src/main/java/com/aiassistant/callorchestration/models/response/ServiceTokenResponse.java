package com.aiassistant.callorchestration.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ServiceTokenResponse {
    String token;
    long expiresIn;
}
