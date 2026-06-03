package com.aiassistant.callorchestration.security;

import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
import com.aiassistant.callorchestration.exceptions.DownstreamServiceException;
import com.aiassistant.callorchestration.models.request.ServiceTokenRequest;
import com.aiassistant.callorchestration.models.response.ServiceTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ServiceTokenClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenClient.class);
    private static final Duration REFRESH_BUFFER = Duration.ofSeconds(30);

    private final RestClient authServiceRestClient;
    private final SecretsConfiguration secretsConfiguration;

    private final ConcurrentMap<String, CachedToken> cache = new ConcurrentHashMap<>();

    public ServiceTokenClient(@Qualifier("authServiceRestClient") RestClient authServiceRestClient,
                              SecretsConfiguration secretsConfiguration) {
        this.authServiceRestClient = authServiceRestClient;
        this.secretsConfiguration = secretsConfiguration;
    }

    public String getToken(String audience, List<String> scopes) {
        String cacheKey = audience + "|" + String.join(",", scopes);
        CachedToken existing = cache.get(cacheKey);
        if (existing != null && Instant.now().isBefore(existing.expiresAt().minus(REFRESH_BUFFER))) {
            return existing.token();
        }
        return refresh(cacheKey, audience, scopes);
    }

    private synchronized String refresh(String cacheKey, String audience, List<String> scopes) {
        CachedToken existing = cache.get(cacheKey);
        if (existing != null && Instant.now().isBefore(existing.expiresAt().minus(REFRESH_BUFFER))) {
            return existing.token();
        }

        SecretsConfiguration.AuthService creds = secretsConfiguration.getAuthService();
        ServiceTokenRequest body = new ServiceTokenRequest(
                creds.getClientId(), creds.getClientSecret(), audience, scopes);

        try {
            ServiceTokenResponse response = authServiceRestClient.post()
                    .uri("/api/internal/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ServiceTokenResponse.class);

            if (response == null || response.getToken() == null) {
                throw new DownstreamServiceException("auth-service returned empty service token");
            }

            CachedToken fresh = new CachedToken(
                    response.getToken(),
                    Instant.now().plusSeconds(response.getExpiresIn()));
            cache.put(cacheKey, fresh);
            log.info("Service token refreshed for audience={} scopes={}, expires in {}s", audience, scopes, response.getExpiresIn());
            return fresh.token();
        } catch (RestClientException ex) {
            throw new DownstreamServiceException("Failed to fetch service token from auth-service", ex);
        }
    }

    private record CachedToken(String token, Instant expiresAt) {}
}
