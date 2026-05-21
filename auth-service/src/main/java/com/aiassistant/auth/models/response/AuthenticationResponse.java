package com.aiassistant.auth.models.response;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class AuthenticationResponse {
    String token;                       // access token
    String refreshToken;                          // refresh token
    @Builder.Default String tokenType = "Bearer"; // default type
    long expiresIn;                               // seconds until access token expiry
    String message;                               // optional info
}
