package com.aiassistant.knowledge.security.token;

import java.util.Map;

public final class TokenPrincipal {
    private final String subject;
    private final Map<String, Object> attributes;

    public TokenPrincipal(String subject, Map<String, Object> attributes) {
        this.subject = subject;
        this.attributes = Map.copyOf(attributes);
    }

    public String getSubject() {
        return subject;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
