package com.aiassistant.incomingcall.security;

import com.aiassistant.incomingcall.configuration.SecretsConfiguration;
import com.aiassistant.incomingcall.configuration.ServiceConfiguration;
import com.aiassistant.incomingcall.exceptions.DownstreamServiceException;
import com.aiassistant.incomingcall.models.request.ServiceTokenRequest;
import com.aiassistant.incomingcall.models.response.ServiceTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Component
public class ServiceTokenClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenClient.class);
    // Refresh ~30s before the token actually expires to avoid cross-call expiry races.
    private static final Duration REFRESH_BUFFER = Duration.ofSeconds(30);

    private final RestClient authServiceRestClient;
    private final ServiceConfiguration serviceConfiguration;
    private final SecretsConfiguration secretsConfiguration;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public ServiceTokenClient(@Qualifier("authServiceRestClient") RestClient authServiceRestClient,
                              ServiceConfiguration serviceConfiguration,
                              SecretsConfiguration secretsConfiguration) {
        this.authServiceRestClient = authServiceRestClient;
        this.serviceConfiguration = serviceConfiguration;
        this.secretsConfiguration = secretsConfiguration;
    }

    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minus(REFRESH_BUFFER))) {
            return cachedToken;
        }
        refresh();
        return cachedToken;
    }

    private void refresh() {
        ServiceConfiguration.AuthService cfg = serviceConfiguration.getAuthService();
        SecretsConfiguration.AuthService creds = secretsConfiguration.getAuthService();

        List<String> scopes = cfg.getScopes() == null || cfg.getScopes().isBlank()
                ? List.of()
                : Arrays.stream(cfg.getScopes().split("[,\\s]+")).filter(s -> !s.isBlank()).toList();

        ServiceTokenRequest body = new ServiceTokenRequest(
                creds.getClientId(),
                creds.getClientSecret(),
                cfg.getAudience(),
                scopes);

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

            this.cachedToken = response.getToken();
            this.expiresAt = Instant.now().plusSeconds(response.getExpiresIn());
            log.info("Service token refreshed, expires in {}s", response.getExpiresIn());
        } catch (RestClientException ex) {
            throw new DownstreamServiceException("Failed to fetch service token from auth-service", ex);
        }
    }
}
