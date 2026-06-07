package com.aiassistant.subscription.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("businessAccessGuard")
public class BusinessAccessGuard {

    public static final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";

    public boolean canAccess(String pathBusinessId) {
        if (pathBusinessId == null) return false;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            return false;
        }
        if (principal.hasRole(ROLE_PLATFORM_ADMIN)) return true;
        return pathBusinessId.equals(principal.getBusinessId());
    }
}
