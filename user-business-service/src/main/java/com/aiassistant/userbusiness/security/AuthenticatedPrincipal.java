package com.aiassistant.userbusiness.security;

import java.util.List;

public final class AuthenticatedPrincipal {

    private final String subject;
    private final String uid;
    private final String businessId;
    private final String audience;
    private final List<String> roles;
    private final List<String> scopes;

    public AuthenticatedPrincipal(String subject,
                                  String uid,
                                  String businessId,
                                  String audience,
                                  List<String> roles,
                                  List<String> scopes) {
        this.subject = subject;
        this.uid = uid;
        this.businessId = businessId;
        this.audience = audience;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public String getSubject() {
        return subject;
    }

    public String getUid() {
        return uid;
    }

    public String getBusinessId() {
        return businessId;
    }

    public String getAudience() {
        return audience;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
