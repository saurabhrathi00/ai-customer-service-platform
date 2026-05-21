package com.aiassistant.userbusiness.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("businessAccessGuard")
public class BusinessAccessGuard {

    public static final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";

    /**
     * True when the caller is a platform admin OR the path's businessId matches
     * the businessId claim on the JWT. Used from @PreAuthorize to enforce tenant
     * isolation on every /api/v1/business/{id}/** endpoint.
     */
    public boolean canAccess(String pathBusinessId) {
        if (pathBusinessId == null) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            return false;
        }
        if (principal.hasRole(ROLE_PLATFORM_ADMIN)) {
            return true;
        }
        return pathBusinessId.equals(principal.getBusinessId());
    }
}
