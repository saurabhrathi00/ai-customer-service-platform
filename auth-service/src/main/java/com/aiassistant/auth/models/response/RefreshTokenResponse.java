package com.aiassistant.auth.models.response;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;


@Builder
@Jacksonized
public class RefreshTokenResponse {
    @NonNull
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresIn; // seconds until expiry
    private String message;
}
