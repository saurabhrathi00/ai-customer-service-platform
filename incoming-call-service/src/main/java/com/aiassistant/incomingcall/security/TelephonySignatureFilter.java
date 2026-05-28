package com.aiassistant.incomingcall.security;

import com.aiassistant.incomingcall.provider.TelephonyProviderRegistry;
import com.aiassistant.incomingcall.provider.TelephonyProvider;
import com.aiassistant.incomingcall.provider.TelephonySignatureInvalidException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads /api/v1/webhook/{provider}/... from the URL, resolves the provider,
 * and delegates signature verification. JSON-bodied providers can re-read the
 * body downstream thanks to the ContentCachingRequestWrapper.
 */
@RequiredArgsConstructor
public class TelephonySignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TelephonySignatureFilter.class);

    private static final Pattern WEBHOOK_PATH = Pattern.compile("/api/v1/webhook/([^/]+)/.*");

    private final TelephonyProviderRegistry registry;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WEBHOOK_PATH.matcher(request.getServletPath()).matches();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Matcher m = WEBHOOK_PATH.matcher(request.getServletPath());
        if (!m.matches()) {
            chain.doFilter(request, response);
            return;
        }
        String providerName = m.group(1);
        Optional<TelephonyProvider> maybeProvider = registry.find(providerName);
        if (maybeProvider.isEmpty()) {
            log.warn("Unknown telephony provider in URL: {}", providerName);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        // Touching parameters here ensures form-encoded bodies (Twilio) are parsed by the
        // container before the body is consumed for caching, so getParameterMap() still works.
        wrapped.getParameterMap();

        try {
            maybeProvider.get().verifySignature(wrapped);
        } catch (TelephonySignatureInvalidException ex) {
            log.warn("Signature verification failed for provider={}: {}", providerName, ex.getMessage());
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        chain.doFilter(wrapped, response);
    }
}
