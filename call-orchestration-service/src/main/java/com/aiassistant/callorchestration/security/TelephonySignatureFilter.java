package com.aiassistant.callorchestration.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Stub: per-provider signature validation will be delegated to the telephony provider
 * strategy once the provider package gains a TelephonyProvider abstraction (mirror
 * incoming-call-service). For now, lets webhook calls through and logs.
 */
public class TelephonySignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TelephonySignatureFilter.class);
    private static final Pattern WEBHOOK_PATH = Pattern.compile("/api/v1/webhook/([^/]+)/.*");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WEBHOOK_PATH.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        log.debug("Webhook passthrough: {}", request.getRequestURI());
        chain.doFilter(request, response);
    }
}
