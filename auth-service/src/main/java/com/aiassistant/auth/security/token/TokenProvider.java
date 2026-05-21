package com.aiassistant.auth.security.token;

import java.time.Duration;
import java.util.Map;

public interface TokenProvider {
    String tokenType(); // e.g., "Bearer" or "DPoP"
    String issue(String subject, Map<String, Object> attributes, Duration ttl);
    boolean validate(String token);
    TokenPrincipal parse(String token); // subject + attributes
}

