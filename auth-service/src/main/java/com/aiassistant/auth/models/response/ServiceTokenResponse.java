package com.aiassistant.auth.models.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ServiceTokenResponse {
    private String token;
    private long expiresIn; // seconds
}
